# CBSE Examination & Evaluation Management System (EEMS) 2026
### Version 2.0 — Production-Grade CLI | Java 21 + PostgreSQL

---

## Table of Contents
1. [Project Overview](#overview)
2. [Architecture](#architecture)
3. [OOP Concepts Applied](#oop)
4. [Database Design & Normalization](#database)
5. [ACID Compliance](#acid)
6. [Security Design](#security)
7. [CBSE Policy Compliance](#cbse)
8. [Setup & Installation](#setup)
9. [Running the Application](#run)
10. [File Structure](#structure)

---

## 1. Project Overview <a name="overview"></a>

A full production-grade CLI system aligning with **CBSE 2026 Digital Standards** for:
- **Class 12**: On-Screen Marking (OSM) with full anonymization
- **Class 10**: Physical compliance with section-wise mark validation

---

## 2. Architecture <a name="architecture"></a>

```
┌──────────────────────────────────────────────────────────┐
│  Presentation Layer  — Main.java (CLI Router)            │
│  ConsoleUI.java       — ANSI colours, tables, input      │
├──────────────────────────────────────────────────────────┤
│  Service Layer                                           │
│  AuthService.java     — Login, BCrypt, lockout           │
│  StudentPortal.java   — Student-facing menus             │
│  StaffPortal.java     — Role-aware staff menus           │
│  EvaluationEngine.java— OSM + Physical marking logic     │
├──────────────────────────────────────────────────────────┤
│  Repository Layer — DataRepository.java (DAO)            │
│  All SQL here. Services never touch JDBC directly.       │
├──────────────────────────────────────────────────────────┤
│  Infrastructure                                          │
│  DatabaseManager.java — HikariCP connection pool         │
│  AppConfig.java       — Dotenv-based config loader       │
├──────────────────────────────────────────────────────────┤
│  PostgreSQL 15+  (ACID-compliant persistence)            │
└──────────────────────────────────────────────────────────┘
```

---

## 3. OOP Concepts Applied <a name="oop"></a>

| Principle | Where Applied |
|-----------|---------------|
| **Encapsulation** | All model fields are `private`; access via getters/setters only. `passwordHash` never exposed externally. |
| **Abstraction** | `DataRepository` hides all JDBC/SQL behind clean method contracts. Services call `repo.aeMarkScript(...)` — never raw SQL. |
| **Inheritance / Enums** | `UserRole`, `ClassLevel`, `ScriptStatus` encode domain hierarchies. `ClassLevel.fromInt()` factory creates objects safely. |
| **Polymorphism** | `Main.routeToPortal()` dispatches to `StudentPortal` or `StaffPortal` based on runtime role. `StaffPortal.handleChoice()` switches behaviour per role. |
| **Single Responsibility** | `AuthService` owns only authentication. `EvaluationEngine` owns only marking workflow. `ConsoleUI` owns only display. |
| **Factory Methods** | `User.newStudent(...)` and `User.newStaff(...)` are static factories for safe object construction. |
| **Singleton** | `DatabaseManager` and `AppConfig` are singletons — one pool and one config object per JVM. |
| **Immutability** | `AuditLog` is declared `final` with no setters — audit records cannot be mutated after creation. |
| **State Machine** | `Script.canTransitionTo(ScriptStatus)` enforces valid lifecycle transitions. |

---

## 4. Database Design & Normalization <a name="database"></a>

### Tables

```
users                — All system users (students + staff)
exam_centers         — Extracted to 3NF (center info not repeated in exams)
exams                — Exam schedule + paper configuration
student_exam_enrollment — M:M bridge between students and exams
scripts              — Answer scripts with anonymization
audit_logs           — Append-only forensic trail
```

### Normalization Levels

**1NF (First Normal Form)**
- Every column is atomic (single value per cell)
- No repeating groups or array columns
- `section_a_marks`, `section_b_marks` etc. are separate numeric columns

**2NF (Second Normal Form)**
- All non-key attributes depend on the ENTIRE primary key
- `student_exam_enrollment` uses a composite unique constraint `(student_id, exam_id)`
- No partial dependencies

**3NF (Third Normal Form)**
- No transitive dependencies
- `center_name` and `center_code` were extracted into `exam_centers` table
- `exams.center_id` references `exam_centers.center_id` (FK)
- `class_level` description is in the `ClassLevel` enum — not duplicated in DB rows

**BCNF (Boyce-Codd Normal Form)**
- Every determinant is a candidate key
- `roll_number UNIQUE`, `email UNIQUE`, `masked_id UNIQUE` enforce candidate keys

### Entity-Relationship Summary
```
users (1) ──< student_exam_enrollment >── (1) exams
users (1) ──< scripts
exams (1) ──< scripts
exam_centers (1) ──< exams
users (1) ──< audit_logs
```

---

## 5. ACID Compliance <a name="acid"></a>

### Atomicity
```java
// coordinatorFinalize in DataRepository.java
conn.setAutoCommit(false);
try {
    lockRow(scriptId);           // Step 1
    updateScriptStatus(...)      // Step 2
    conn.commit();               // Both or neither
} catch (Exception e) {
    conn.rollback();             // Undo everything on any failure
    throw e;
}
```

### Consistency
- DB-level: `CHECK (role IN ('STUDENT','INVIGILATOR',...))` prevents invalid role values
- App-level: `Script.canTransitionTo()` prevents invalid status jumps (e.g. PENDING → UPLOADED)
- Marks `CHECK (ae_marks >= 0)` prevents negative marks at DB level

### Isolation
- Default: `READ_COMMITTED` for all read queries
- **Upgraded to `TRANSACTION_SERIALIZABLE`** for `coordinatorFinalize` to prevent:
  - Dirty reads, non-repeatable reads, phantom reads during concurrent finalization
- Row-level `SELECT ... FOR UPDATE` prevents two coordinators finalizing the same script simultaneously

### Durability
- PostgreSQL uses **Write-Ahead Logging (WAL)**
- All committed transactions are written to disk before acknowledgement
- Crash recovery replays WAL to restore consistent state

---

## 6. Security Design <a name="security"></a>

| Threat | Mitigation |
|--------|-----------|
| SQL Injection | 100% PreparedStatements — no string concatenation in SQL |
| Password breach | BCrypt cost=12 (~300ms per hash — brute-force resistant) |
| Credential leakage | `.env` file loaded at runtime; never hard-coded or in version control |
| Brute-force login | 5 attempt lockout in `AuthService` |
| Evaluator bias | Class 12 evaluators see ONLY `maskedId` (BX-2026-XXXXXX) — never student name/roll |
| Concurrent tampering | `SELECT FOR UPDATE` + `SERIALIZABLE` isolation on finalization |
| Audit trail | Every action logged in `audit_logs` (append-only at DB level) |
| Privilege escalation | Every service method checks `currentUser.getRole()` before proceeding |

---

## 7. CBSE Policy Compliance <a name="cbse"></a>

### Class 12 — On-Screen Marking (OSM)
- Answer sheets scanned at NIC facility after collection
- `MaskIdGenerator` generates cryptographically random `BX-YYYY-XXXXXX` IDs
- AE/HE see ONLY the masked ID — `student_id` column excluded from AE/HE queries
- COORDINATOR role has access to full `student_id` linkage for final verification
- HE reviews minimum 10% of AE-marked scripts (`flagRandomScriptsForHEReview()`)
- Variance > 5 marks triggers DISPUTED flag + re-evaluation

### Class 10 — Physical Compliance
- Section-wise marks (A/B/C/D) must be entered separately
- Section sum validated against total before saving (`isSectionWiseValid()`)
- Scripts use `PHY-<rollNumber>` identifier (physical scripts don't require anonymization)
- In-app rules remind examiners of handwriting legibility standards

### Multi-Layer Verification
```
PENDING → AE_MARKED → HE_REVIEWED → COORDINATOR_VERIFIED → FINALIZED → UPLOADED
                   ↘                ↗
                    DISPUTED ───────
```

---

## 8. Setup & Installation <a name="setup"></a>

### Prerequisites
- Java 21+
- Maven 3.8+
- PostgreSQL 14+

### Step 1 — Database Setup
```sql
CREATE DATABASE cbse_eems;
CREATE USER cbse_admin WITH PASSWORD 'YourSecurePassword123!';
GRANT ALL PRIVILEGES ON DATABASE cbse_eems TO cbse_admin;
```

### Step 2 — Environment Configuration
```bash
cp .env.example .env
# Edit .env with your DB credentials
```

### Step 3 — Build
```bash
mvn clean package -q
```

### Step 4 — Run
```bash
java -jar target/board-exam-management-cli-2.0.0-CBSE2026.jar
```

The schema is created automatically on first run (idempotent `IF NOT EXISTS`).

---

## 9. Running the Application <a name="run"></a>

### First-time Setup Flow
```
1. Register Admin staff       (option 3 from main menu)
2. Login as Admin
3. Create Exams               (Admin → Create New Exam)
4. Register Students          (option 2 from main menu)
5. Enroll Students in Exams   (Admin → Enroll Student)
6. Generate Scripts           (Admin → Generate Scripts)
```

### Evaluation Flow (Class 12 OSM)
```
1. Admin: Generate Scripts for Exam     → Scripts created with masked IDs
2. AE Login: Load OSM Marking Queue     → Enter marks (sees only BX-YYYY-XXXXX)
3. HE Login: Trigger 10% HE Flagging   → Random 10% flagged
4. HE Login: Start HE Review            → Review flagged scripts
5. Coordinator: Finalize Scripts        → SERIALIZABLE transaction
6. Coordinator: Upload to CBSE Portal  → Status → UPLOADED
```

### Evaluation Flow (Class 10 Physical)
```
1. Admin: Generate Scripts for Exam     → Scripts with PHY-<roll> IDs
2. AE Login: Load Marking Queue         → Enter Section A/B/C/D marks separately
3. HE Login: Trigger + Review 10%      → Same as Class 12
4. Coordinator: Finalize → Upload
```

---

## 10. File Structure <a name="structure"></a>

```
Board-Exam-Management-CLI/
│
├── .env                          ← Credentials (never commit)
├── .env.example                  ← Template for .env
├── .gitignore
├── pom.xml                       ← Java 21, PostgreSQL, HikariCP, BCrypt, Logback
│
└── src/main/java/com/boardexam/
    ├── Main.java                 ← Entry point + application router
    ├── DatabaseManager.java      ← HikariCP pool + schema bootstrap
    │
    ├── config/
    │   └── AppConfig.java        ← Dotenv loader (Singleton)
    │
    ├── model/                    ← POJOs / Domain Objects
    │   ├── User.java             ← Student + Staff entity
    │   ├── Exam.java             ← Examination schedule entity
    │   ├── Script.java           ← Answer script with anonymization
    │   ├── AuditLog.java         ← Immutable audit record
    │   ├── UserRole.java         ← Enum: STUDENT, AE, HE, COORDINATOR, ADMIN
    │   ├── ClassLevel.java       ← Enum: CLASS_10 (PHYSICAL), CLASS_12 (OSM)
    │   └── ScriptStatus.java     ← Enum: PENDING → ... → UPLOADED state machine
    │
    ├── repository/
    │   └── DataRepository.java   ← All SQL / JDBC (DAO pattern)
    │
    ├── service/
    │   ├── AuthService.java      ← Login, BCrypt, lockout, registration
    │   ├── StudentPortal.java    ← Student-facing CLI menus
    │   ├── StaffPortal.java      ← Role-adaptive staff menus
    │   └── EvaluationEngine.java ← OSM + Physical marking + finalization
    │
    └── util/
        ├── ConsoleUI.java        ← ANSI colours, tables, input helpers
        ├── PasswordUtil.java     ← BCrypt hashing + policy validation
        └── MaskIdGenerator.java  ← SecureRandom BX-YYYY-XXXXXX generator
```
