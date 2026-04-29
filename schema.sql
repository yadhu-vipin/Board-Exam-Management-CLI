-- ============================================================
-- CBSE EEMS 2026 — Full Database Schema
-- PostgreSQL 14+
-- Run once against your cbse_eems database.
-- The application also auto-creates these on first launch.
-- ============================================================

-- ── 1. Users (1NF, 2NF, 3NF compliant) ─────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(150)  NOT NULL,
    email         VARCHAR(200)  UNIQUE NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,           -- BCrypt, never plaintext
    role          VARCHAR(25)   NOT NULL
                    CHECK (role IN ('STUDENT','INVIGILATOR',
                           'ASSISTANT_EXAMINER','HEAD_EXAMINER',
                           'COORDINATOR','ADMIN')),
    class_level   INT           CHECK (class_level IN (10, 12)),
    roll_number   VARCHAR(20)   UNIQUE,             -- CBSE Roll Number (students)
    staff_code    VARCHAR(20)   UNIQUE,             -- Staff ID (staff members)
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_login    TIMESTAMPTZ,
    -- Referential integrity constraints
    CONSTRAINT chk_student_has_roll
        CHECK (role != 'STUDENT' OR roll_number IS NOT NULL),
    CONSTRAINT chk_staff_has_code
        CHECK (role = 'STUDENT' OR staff_code IS NOT NULL)
);

-- ── 2. Exam Centers (extracted for 3NF — no transitive deps) ─
CREATE TABLE IF NOT EXISTS exam_centers (
    center_id   SERIAL PRIMARY KEY,
    center_code VARCHAR(20)  UNIQUE NOT NULL,       -- e.g. DEL-0021
    center_name VARCHAR(150) NOT NULL,
    district    VARCHAR(100),
    state       VARCHAR(100),
    pin_code    VARCHAR(10)
);

-- ── 3. Exams ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS exams (
    exam_id           SERIAL PRIMARY KEY,
    subject           VARCHAR(80)  NOT NULL,
    subject_code      VARCHAR(10)  NOT NULL,        -- CBSE code e.g. 042 (Physics)
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
);

-- ── 4. Student-Exam Enrollment (M:M bridge table) ─────────────
CREATE TABLE IF NOT EXISTS student_exam_enrollment (
    enrollment_id  SERIAL PRIMARY KEY,
    student_id     INT  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exam_id        INT  NOT NULL REFERENCES exams(exam_id),
    seat_number    VARCHAR(10),
    is_present     BOOLEAN DEFAULT NULL,            -- NULL = not yet marked
    UNIQUE (student_id, exam_id)                    -- 2NF: PK is enrollment_id
);

-- ── 5. Scripts (Core Anonymization Table) ─────────────────────
-- CLASS 12: evaluators see ONLY masked_id (BX-2026-XXXXXX)
-- CLASS 10: uses PHY-<rollnumber> (physical scripts)
CREATE TABLE IF NOT EXISTS scripts (
    script_id      SERIAL PRIMARY KEY,
    student_id     INT          NOT NULL REFERENCES users(id),
    masked_id      VARCHAR(20)  UNIQUE NOT NULL,    -- Anonymized ID shown to AE/HE
    exam_id        INT          NOT NULL REFERENCES exams(exam_id),
    subject        VARCHAR(80)  NOT NULL,
    class_level    INT          NOT NULL CHECK (class_level IN (10, 12)),
    -- Status state machine: PENDING → AE_MARKED → HE_REVIEWED → COORDINATOR_VERIFIED → FINALIZED → UPLOADED
    status         VARCHAR(25)  NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','AE_MARKED','HE_REVIEWED',
                            'COORDINATOR_VERIFIED','FINALIZED','UPLOADED','DISPUTED')),
    -- Marks (multi-layer verification)
    ae_marks           INT DEFAULT 0,               -- Assistant Examiner marks
    he_marks           INT DEFAULT 0,               -- Head Examiner review marks
    final_marks        INT DEFAULT 0,               -- Coordinator-approved final
    he_review_needed   BOOLEAN DEFAULT FALSE,       -- True for random 10% sample
    -- Section-wise marks (Class 10 physical compliance)
    section_a_marks    INT DEFAULT 0,               -- MCQ / Objective
    section_b_marks    INT DEFAULT 0,               -- Very Short Answer
    section_c_marks    INT DEFAULT 0,               -- Short Answer
    section_d_marks    INT DEFAULT 0,               -- Long Answer / Case / Map
    -- Staff audit trail (who touched this script)
    ae_staff_id        INT REFERENCES users(id),
    he_staff_id        INT REFERENCES users(id),
    coordinator_id     INT REFERENCES users(id),
    -- Timestamps
    ae_marked_at       TIMESTAMPTZ,
    he_reviewed_at     TIMESTAMPTZ,
    finalized_at       TIMESTAMPTZ,
    dispute_reason     TEXT,
    CONSTRAINT chk_marks_non_negative
        CHECK (ae_marks >= 0 AND he_marks >= 0 AND final_marks >= 0)
);

-- ── 6. Audit Logs (Append-Only, Forensic) ─────────────────────
-- RBAC note: No application role has DELETE/UPDATE on this table.
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id        SERIAL PRIMARY KEY,
    user_id       INT          NOT NULL REFERENCES users(id),
    action        VARCHAR(50)  NOT NULL,            -- e.g. AE_MARKS_ENTERED
    target_entity VARCHAR(30),                      -- e.g. 'script', 'exam'
    target_id     INT,
    detail        TEXT,                             -- Human-readable context
    timestamp     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_address    VARCHAR(45)                       -- IPv4 or IPv6
);

-- ── Indexes ───────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_scripts_status     ON scripts(status);
CREATE INDEX IF NOT EXISTS idx_scripts_exam       ON scripts(exam_id);
CREATE INDEX IF NOT EXISTS idx_scripts_masked     ON scripts(masked_id);
CREATE INDEX IF NOT EXISTS idx_audit_user         ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp    ON audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_enrollment_student ON student_exam_enrollment(student_id);
CREATE INDEX IF NOT EXISTS idx_users_roll         ON users(roll_number);
CREATE INDEX IF NOT EXISTS idx_users_email        ON users(email);

-- ── Seed: Default Admin (password: Admin@2026!) ───────────────
-- BCrypt hash of "Admin@2026!" with cost=12
-- CHANGE THIS PASSWORD IMMEDIATELY IN PRODUCTION
INSERT INTO users (name, email, password_hash, role, staff_code)
VALUES (
    'System Administrator',
    'admin@cbse.nic.in',
    '$2a$12$exampleHashChangeThisBeforeProduction00000000000000000000',
    'ADMIN',
    'CBSE-ADMIN-001'
) ON CONFLICT (email) DO NOTHING;
