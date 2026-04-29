package com.boardexam;

import com.boardexam.config.AppConfig;
import com.boardexam.repository.DataRepository;
import com.boardexam.service.AuthService;
import com.boardexam.service.StaffPortal;
import com.boardexam.service.StudentPortal;
import com.boardexam.model.User;
import com.boardexam.util.ConsoleUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║   CBSE — Examination & Evaluation Management System (EEMS) 2026 v2.0   ║
 * ║   Central Board of Secondary Education, New Delhi                       ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Entry point — acts as the application "Router".
 *
 * ARCHITECTURE LAYERS:
 * ┌────────────────────────────────────────────────┐
 * │  CLI (Main.java)          ← You are here       │  Presentation Layer
 * ├────────────────────────────────────────────────┤
 * │  Service Layer                                 │  Business Logic
 * │  AuthService, StudentPortal, StaffPortal,      │
 * │  EvaluationEngine                              │
 * ├────────────────────────────────────────────────┤
 * │  Repository Layer (DataRepository)             │  Data Access (DAO)
 * ├────────────────────────────────────────────────┤
 * │  DatabaseManager (HikariCP Pool)               │  Connection Pool
 * ├────────────────────────────────────────────────┤
 * │  PostgreSQL (ACID-compliant)                   │  Persistence
 * └────────────────────────────────────────────────┘
 *
 * OOP PRINCIPLES APPLIED:
 *  - Encapsulation : All fields private; access through methods
 *  - Inheritance   : UserRole/ClassLevel hierarchies; ScriptStatus state machine
 *  - Polymorphism  : Portal behaviour adapts to UserRole at runtime
 *  - Abstraction   : CLI never touches SQL; Repository hides JDBC complexity
 *  - Single Resp.  : Each class owns exactly one concern
 *
 * NORMALIZATION:
 *  1NF — All columns atomic (no repeating groups, no arrays in rows)
 *  2NF — All non-key attributes depend on the FULL primary key
 *  3NF — No transitive dependencies (exam_centers extracted from exams)
 *  BCNF— Candidate keys handled via UNIQUE constraints
 *
 * ACID (PostgreSQL):
 *  Atomicity   — All multi-step ops use BEGIN/COMMIT + ROLLBACK on failure
 *  Consistency — CHECK constraints + application-layer state machine
 *  Isolation   — READ_COMMITTED default; SERIALIZABLE for finalization
 *  Durability  — PostgreSQL WAL (Write-Ahead Log) guarantees persistence
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ConsoleUI.printBanner();

        // ── Bootstrap ───────────────────────────────────────────────────────
        DatabaseManager db = null;
        try {
            log.info("Starting CBSE EEMS 2026...");
            AppConfig.getInstance(); // Will throw if .env is missing critical keys

            db = DatabaseManager.getInstance();
            db.initSchema(); // Idempotent — safe on every startup

            DataRepository repo = new DataRepository(db);
            AuthService auth    = new AuthService(repo);

            // ── Main Application Loop ────────────────────────────────────────
            while (true) {
                ConsoleUI.printSectionHeader("Main Menu");
                ConsoleUI.println("  1. Login");
                ConsoleUI.println("  2. Register as Student");
                ConsoleUI.println("  3. Register Staff (Admin use)");
                ConsoleUI.println("  0. Exit");

                int choice = ConsoleUI.promptInt("Choice", 0, 3);

                switch (choice) {
                    case 1 -> {
                        User user = auth.login();
                        if (user != null) {
                            routeToPortal(user, repo, auth);
                        }
                    }
                    case 2 -> auth.registerStudent();
                    case 3 -> auth.registerStaff();
                    case 0 -> {
                        ConsoleUI.println();
                        ConsoleUI.bold("  Thank you for using CBSE EEMS 2026.");
                        ConsoleUI.info("  All examination data is secured and compliant with CBSE 2026 standards.");
                        ConsoleUI.println();
                        System.exit(0);
                    }
                }
            }

        } catch (IllegalStateException e) {
            // Missing environment variable or schema failure
            ConsoleUI.error("CONFIGURATION ERROR: " + e.getMessage());
            ConsoleUI.error("Please check your .env file. Refer to .env.example for required keys.");
            log.error("Startup failed", e);
            System.exit(1);

        } catch (Exception e) {
            ConsoleUI.error("FATAL ERROR: " + e.getMessage());
            log.error("Unhandled exception at startup", e);
            System.exit(2);

        } finally {
            if (db != null) {
                db.close();
                log.info("Database pool closed. Bye.");
            }
        }
    }

    /**
     * Routes the logged-in user to the appropriate portal based on their role.
     * This is the polymorphic dispatch point — same login, different experience.
     */
    private static void routeToPortal(User user, DataRepository repo, AuthService auth) {
        try {
            if (user.isStudent()) {
                new StudentPortal(repo, user).run();
            } else {
                new StaffPortal(repo, user).run();
            }
        } finally {
            auth.logout();
        }
    }
}
