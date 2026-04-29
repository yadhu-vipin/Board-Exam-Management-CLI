package com.boardexam;

import com.boardexam.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseManager — Connection Pooling + Schema Bootstrap
 *
 * Uses HikariCP (the fastest JDBC pool) configured for PostgreSQL.
 * 
 * ACID PROPERTIES ENFORCED HERE:
 * ┌─────────────┬────────────────────────────────────────────────────────────┐
 * │ Atomicity   │ All service methods wrap multi-step ops in BEGIN/COMMIT.   │
 * │             │ On any failure, ROLLBACK is called.                        │
 * │ Consistency │ DB CHECK constraints + app-layer validation (Script state  │
 * │             │ machine) prevent invalid transitions.                       │
 * │ Isolation   │ HikariCP default: READ_COMMITTED. SERIALIZABLE used for   │
 * │             │ mark finalization to prevent phantom reads.                 │
 * │ Durability  │ PostgreSQL WAL (Write-Ahead Logging) ensures committed     │
 * │             │ transactions survive crashes.                               │
 * └─────────────┴────────────────────────────────────────────────────────────┘
 *
 * Singleton — one pool per JVM.
 */
public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private static HikariDataSource dataSource = new HikariDataSource();

    private DatabaseManager() {
        AppConfig cfg = AppConfig.getInstance();
        HikariConfig hikari = new HikariConfig();

        hikari.setJdbcUrl(cfg.dbUrl());
        hikari.setUsername(cfg.dbUser());
        hikari.setPassword(cfg.dbPassword());
        hikari.setDriverClassName("org.postgresql.Driver");

        // Pool tuning — appropriate for a single-server CBSE deployment
        hikari.setMaximumPoolSize(cfg.poolSize());
        hikari.setMinimumIdle(2);
        hikari.setIdleTimeout(30_000);
        hikari.setConnectionTimeout(10_000);
        hikari.setMaxLifetime(1_800_000);   // 30 min

        // Ensures each borrowed connection is valid
        hikari.setConnectionTestQuery("SELECT 1");
        hikari.setPoolName("CBSE-EEMS-Pool");

        // PostgreSQL performance settings
        hikari.addDataSourceProperty("prepareThreshold", "5");
        hikari.addDataSourceProperty("preparedStatementCacheQueries", "250");
        hikari.addDataSourceProperty("preparedStatementCacheSizeMiB", "5");

        DatabaseManager.dataSource = new HikariDataSource(hikari);
        log.info("HikariCP pool '{}' initialised. Max size: {}",
                 hikari.getPoolName(), cfg.poolSize());
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database connection pool closed.");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Schema Bootstrap — Creates all tables if they don't exist.
    // This is idempotent (IF NOT EXISTS) — safe to call on every startup.
    // ──────────────────────────────────────────────────────────────────────────

    public void initSchema() {
        log.info("Bootstrapping CBSE EEMS schema...");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // ── Table 1: users (1NF, 2NF, 3NF compliant) ──────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            SERIAL PRIMARY KEY,
                    name          VARCHAR(150)  NOT NULL,
                    email         VARCHAR(200)  UNIQUE NOT NULL,
                    password_hash VARCHAR(255)  NOT NULL,
                    role          VARCHAR(25)   NOT NULL
                                    CHECK (role IN ('STUDENT','INVIGILATOR',
                                           'ASSISTANT_EXAMINER','HEAD_EXAMINER',
                                           'COORDINATOR','ADMIN')),
                    class_level   INT           CHECK (class_level IN (10, 12)),
                    roll_number   VARCHAR(20)   UNIQUE,
                    staff_code    VARCHAR(20)   UNIQUE,
                    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
                    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                    last_login    TIMESTAMPTZ,
                    CONSTRAINT chk_student_has_roll
                        CHECK (role != 'STUDENT' OR roll_number IS NOT NULL),
                    CONSTRAINT chk_staff_has_code
                        CHECK (role = 'STUDENT' OR staff_code IS NOT NULL)
                )
            """);

            // ── Table 2: exam_centers (extracted for 3NF compliance) ───────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exam_centers (
                    center_id   SERIAL PRIMARY KEY,
                    center_code VARCHAR(20)  UNIQUE NOT NULL,
                    center_name VARCHAR(150) NOT NULL,
                    district    VARCHAR(100),
                    state       VARCHAR(100),
                    pin_code    VARCHAR(10)
                )
            """);

            // ── Table 3: exams ─────────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exams (
                    exam_id           SERIAL PRIMARY KEY,
                    subject           VARCHAR(80)  NOT NULL,
                    subject_code      VARCHAR(10)  NOT NULL,
                    exam_date         DATE         NOT NULL,
                    start_time        TIME         NOT NULL DEFAULT '10:30:00',
                    duration_minutes  INT          NOT NULL DEFAULT 180,
                    center_id         INT          REFERENCES exam_centers(center_id),
                    class_level       INT          NOT NULL CHECK (class_level IN (10, 12)),
                    total_marks       INT          NOT NULL DEFAULT 80,
                    practical_marks   INT          NOT NULL DEFAULT 20,
                    osm_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
                    CONSTRAINT chk_osm_only_class12
                        CHECK (osm_enabled = FALSE OR class_level = 12)
                )
            """);

            // ── Table 4: student_exam_enrollment ──────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS student_exam_enrollment (
                    enrollment_id  SERIAL PRIMARY KEY,
                    student_id     INT  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    exam_id        INT  NOT NULL REFERENCES exams(exam_id),
                    seat_number    VARCHAR(10),
                    is_present     BOOLEAN DEFAULT NULL,
                    UNIQUE (student_id, exam_id)
                )
            """);

            // ── Table 5: scripts (core anonymization table) ────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS scripts (
                    script_id      SERIAL PRIMARY KEY,
                    student_id     INT          NOT NULL REFERENCES users(id),
                    masked_id      VARCHAR(20)  UNIQUE NOT NULL,
                    exam_id        INT          NOT NULL REFERENCES exams(exam_id),
                    subject        VARCHAR(80)  NOT NULL,
                    class_level    INT          NOT NULL CHECK (class_level IN (10, 12)),
                    status         VARCHAR(25)  NOT NULL DEFAULT 'PENDING'
                                     CHECK (status IN ('PENDING','AE_MARKED',
                                            'HE_REVIEWED','COORDINATOR_VERIFIED',
                                            'FINALIZED','UPLOADED','DISPUTED')),
                    ae_marks           INT DEFAULT 0,
                    he_marks           INT DEFAULT 0,
                    final_marks        INT DEFAULT 0,
                    he_review_needed   BOOLEAN DEFAULT FALSE,
                    section_a_marks    INT DEFAULT 0,
                    section_b_marks    INT DEFAULT 0,
                    section_c_marks    INT DEFAULT 0,
                    section_d_marks    INT DEFAULT 0,
                    ae_staff_id        INT REFERENCES users(id),
                    he_staff_id        INT REFERENCES users(id),
                    coordinator_id     INT REFERENCES users(id),
                    ae_marked_at       TIMESTAMPTZ,
                    he_reviewed_at     TIMESTAMPTZ,
                    finalized_at       TIMESTAMPTZ,
                    dispute_reason     TEXT,
                    CONSTRAINT chk_marks_non_negative
                        CHECK (ae_marks >= 0 AND he_marks >= 0 AND final_marks >= 0)
                )
            """);

            // ── Table 6: audit_logs (append-only, RBAC-protected) ──────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    log_id        SERIAL PRIMARY KEY,
                    user_id       INT          NOT NULL REFERENCES users(id),
                    action        VARCHAR(50)  NOT NULL,
                    target_entity VARCHAR(30),
                    target_id     INT,
                    detail        TEXT,
                    timestamp     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                    ip_address    VARCHAR(45)
                )
            """);

            // ── Indexes for query performance ──────────────────────────────
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_scripts_status    ON scripts(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_scripts_exam      ON scripts(exam_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_user        ON audit_logs(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_timestamp   ON audit_logs(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_enrollment_student ON student_exam_enrollment(student_id)");

            log.info("Schema bootstrap complete. All tables and indexes are ready.");

        } catch (SQLException e) {
            log.error("Schema bootstrap FAILED: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot initialize database schema. Aborting.", e);
        }
    }
}
