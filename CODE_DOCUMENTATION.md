# CBSE EEMS 2026 — Java Code Documentation
### What Every File Does & Which OOP Concepts It Demonstrates

---

## Project Structure at a Glance

```
src/main/java/com/boardexam/
│
├── Main.java                        ← App entry point & router
├── DatabaseManager.java             ← DB connection pool + schema bootstrap
│
├── config/
│   └── AppConfig.java               ← Secure credential loader
│
├── model/                           ← Domain objects (data + behaviour)
│   ├── User.java
│   ├── Exam.java
│   ├── Script.java
│   ├── AuditLog.java
│   ├── UserRole.java                ← Enum
│   ├── ClassLevel.java              ← Enum
│   └── ScriptStatus.java            ← Enum
│
├── service/                         ← Business logic layer
│   ├── AuthService.java
│   ├── StudentPortal.java
│   ├── StaffPortal.java
│   └── EvaluationEngine.java
│
├── repository/
│   └── DataRepository.java          ← All SQL (DAO pattern)
│
└── util/
    ├── ConsoleUI.java               ← All terminal I/O
    ├── PasswordUtil.java            ← BCrypt hashing
    └── MaskIdGenerator.java         ← OSM anonymization
```

---

## Layer 1 — Entry Point

### `Main.java`

**What it does:**
This is the first class the JVM executes. It bootstraps the entire application in a fixed order: initialise the database connection pool, create the schema if it doesn't exist, wire up the service objects, then enter a loop that shows the main menu until the user chooses to exit. When a user successfully logs in, `Main` inspects their role and hands control to either `StudentPortal` or `StaffPortal`. When the loop ends, it cleanly shuts down the connection pool.

**Key responsibilities:**
- Calls `DatabaseManager.initSchema()` on startup (idempotent — safe to run every time)
- Calls `AuthService.login()` and routes the result
- Prints the application banner and About screen
- Handles graceful shutdown via `db.close()`

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Abstraction** | `Main` never knows SQL exists. It calls service methods and trusts them to do the right thing. The entire DB layer is hidden behind `DataRepository`. |
| **Polymorphism (runtime dispatch)** | `routeUser(User user)` checks `user.isStudent()` at runtime and dispatches to a completely different portal. The method signature is the same; the behaviour differs based on the actual object state. |
| **Single Responsibility** | `Main` does one thing — route. All business logic lives in services. All SQL lives in the repository. |
| **Dependency Injection (manual)** | Services are constructed in `main()` and injected with a shared `DataRepository`. No object creates its own dependencies. |

---

### `DatabaseManager.java`

**What it does:**
Manages the entire lifecycle of the PostgreSQL connection pool using HikariCP — the fastest JDBC connection pool available for Java. It is called once at startup to create the pool and again at shutdown to drain it. It also contains `initSchema()`, which runs all six `CREATE TABLE IF NOT EXISTS` statements, creates indexes, and is completely idempotent (safe to call on every launch).

The class documents exactly how each ACID property is enforced at the infrastructure level, using comments that map each property (Atomicity, Consistency, Isolation, Durability) to its PostgreSQL/HikariCP mechanism.

**Key responsibilities:**
- Configures HikariCP: pool size, idle timeout, connection test query, PostgreSQL-specific prepared statement caching
- Creates all 6 tables: `users`, `exam_centers`, `exams`, `student_exam_enrollment`, `scripts`, `audit_logs`
- Creates 8 indexes for query performance
- Exposes `getConnection()` for the repository layer

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Singleton Pattern** | `private static DatabaseManager instance` with a `synchronized getInstance()` method. Only one pool is ever created per JVM run. The `private` constructor prevents direct instantiation. |
| **Encapsulation** | `HikariDataSource` is a private field. No other class can access it directly. Everyone goes through `getConnection()`. |
| **`final` class** | Declared `final` so it cannot be subclassed and the singleton cannot be overridden through inheritance. |
| **Separation of Concerns** | Schema DDL lives here, not scattered across services. One class owns the database structure. |

---

## Layer 2 — Configuration

### `config/AppConfig.java`

**What it does:**
Loads environment variables from the `.env` file using the `dotenv-java` library. Every sensitive credential (DB URL, username, password) is read from this file at runtime — nothing is hardcoded in the source. If a required variable is missing, it throws a clear `IllegalStateException` with a message telling the developer exactly which key is missing and where to set it.

Provides typed convenience methods: `dbUrl()`, `dbUser()`, `dbPassword()`, `poolSize()`, `isDev()`.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Singleton Pattern** | Same `synchronized getInstance()` pattern as `DatabaseManager`. One config load per JVM. |
| **`final` class** | Cannot be subclassed — the singleton contract cannot be broken by inheritance. |
| **Fail-Fast Design** | `get(String key)` throws immediately if a key is missing, rather than returning `null` and causing a `NullPointerException` somewhere deep in the code. |
| **Encapsulation** | The raw `Dotenv` object is private. Callers use named methods like `dbUrl()`, not string literals like `"DB_URL"`. |

---

## Layer 3 — Models (Domain Objects)

The model layer follows the principle that **objects should carry both data and the behaviour that operates on that data**. A `Script` object knows whether its marks are valid. A `User` object knows whether it can mark scripts. This prevents that logic from being scattered across multiple service classes.

---

### `model/User.java`

**What it does:**
Represents any person in the system — both students and staff — in a single class. The `role` field (a `UserRole` enum) determines what kind of user it is. The class stores the BCrypt password hash (never the plaintext password), the CBSE roll number for students, and the staff code for staff.

Beyond storing data, `User` contains business logic methods that answer questions the rest of the application needs to ask constantly:
- `isStudent()` — is this a student?
- `canMarkScripts()` — is this person an AE, HE, or Coordinator?
- `isClass12()` — are they in Class 12 (triggers OSM workflow)?
- `getDisplayIdentifier()` — returns "Roll: DEL12-2601" or "Staff: CBSE-AE-001" depending on the role

It also provides two **factory methods** for construction: `User.newStudent(...)` and `User.newStaff(...)`, which set the role and relevant fields in one call with no ambiguity.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Encapsulation** | All 11 fields are `private`. Access is strictly through getters and setters. The password hash is never exposed as a raw field — callers get `getPasswordHash()` which returns the hash string, not the plaintext. |
| **Factory Method Pattern** | `User.newStudent()` and `User.newStaff()` are static factory methods. They replace overloaded constructors and make the intent clear at the call site: `User.newStudent(...)` is more readable than `new User(name, email, hash, STUDENT, CLASS_12, roll, null, true, null, null)`. |
| **Behaviour in Models** | `isStudent()`, `canMarkScripts()`, `isClass12()` are business rules encoded in the object. This is the OOP principle of keeping behaviour with data. |
| **`@Override` of `equals/hashCode`** | Two `User` objects are equal if and only if their `id` fields match — database identity, not object identity. Essential for using `User` objects in collections. |
| **`@Override toString()`** | Returns a formatted string useful for logging and debugging, calling `getDisplayIdentifier()` so the output is role-aware. |

---

### `model/Exam.java`

**What it does:**
Represents a scheduled CBSE examination paper. Stores the subject, subject code, date, start time, duration, center reference, class level, marks breakdown (theory + practical), and whether OSM is enabled.

Provides derived/computed methods so the rest of the code never has to repeat the calculation:
- `getEndTime()` — calculates start time + duration
- `getFullMarks()` — returns theory + practical total
- `getGradingType()` — delegates to `ClassLevel` enum to return `"OSM"` or `"PHYSICAL"`
- `requiresOSM()` — returns `true` only when the class is 12 AND total marks are 40 or above (the CBSE 2026 threshold)

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Encapsulation** | All fields private; centre name/code accessed via getter. |
| **Delegation** | `getGradingType()` delegates to `classLevel.getGradingType()` rather than duplicating the logic. |
| **Derived Attributes** | `getEndTime()` and `getFullMarks()` are computed on-the-fly rather than stored redundantly — avoids inconsistency between `startTime + duration` and a separately stored `endTime`. |
| **`@Override equals()`** | Two exams are equal if their `examId` matches — database identity. |

---

### `model/Script.java`

**What it does:**
The most complex model in the system. Represents a single student's answer paper as it moves through the entire CBSE evaluation pipeline. Contains multi-layer marks (AE marks, HE marks, final marks), section-wise marks for Class 10, audit timestamps, the anonymization `maskedId`, and the pipeline `status`.

The most important method is `canTransitionTo(ScriptStatus next)`, which implements a **state machine** — it defines exactly which status transitions are legal. A script in `PENDING` state can only move to `AE_MARKED`. A script in `UPLOADED` state cannot move anywhere (terminal state). This prevents any code from putting a script into an illegal state.

Other behaviour methods:
- `isSectionWiseValid(int)` — validates that section A+B+C+D = declared total (Class 10 compliance)
- `hasSignificantVariance()` — returns `true` if |AE marks - HE marks| > 5 (CBSE policy trigger)
- `getEffectiveMarks()` — returns the right marks for the current stage: final marks if verified, HE marks if reviewed, AE marks otherwise

**OOP Concepts:**

| Concept | Where |
|---|---|
| **State Machine Pattern** | `canTransitionTo()` uses a `switch` expression over the current `status` enum to return a boolean. Every possible transition is explicitly listed. The `UPLOADED` case returns `false` unconditionally — it is a terminal state. |
| **Encapsulation of Business Rules** | Variance detection, section validation, and effective-marks logic are all methods on the object itself, not in service classes. |
| **`@Override equals()`** | Identity by `scriptId` — two script objects loaded from different queries for the same row are equal. |
| **Composition** | `Script` references `ClassLevel` and `ScriptStatus` enums rather than storing raw strings. Type safety means it is impossible to set an invalid status. |

---

### `model/AuditLog.java`

**What it does:**
Represents a single audit record — a permanent, immutable log of something that happened in the system (a login, marks being entered, a script being uploaded). Every field is `final`. There are no setters. Once created, an `AuditLog` object cannot be changed.

This models the database table's append-only policy directly in the Java type system: if there are no setters, it is impossible to accidentally mutate an audit record in code.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Immutability** | All 8 fields are `private final`. The class has only a full constructor and getters — no setters anywhere. This is a Value Object in Domain-Driven Design terms. |
| **`final` class** | The class itself is declared `final` — it cannot be subclassed, which means the immutability guarantee cannot be broken by a subclass that adds mutable state. |
| **`@Override toString()`** | Returns a single-line formatted string useful for log output, combining timestamp, user, action, and target entity. |

---

### `model/UserRole.java` — Enum

**What it does:**
Defines the six valid roles in the CBSE system: `STUDENT`, `INVIGILATOR`, `ASSISTANT_EXAMINER`, `HEAD_EXAMINER`, `COORDINATOR`, `ADMIN`. Stored as a `VARCHAR(25)` column in the database, enforced by a `CHECK` constraint, and used throughout the codebase for access-control decisions.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Type Safety via Enum** | Roles cannot be arbitrary strings. `UserRole.ASSISTANT_EXAMINER` is a compile-time constant. A typo like `"ASST_EXAMINER"` would be a compile error, not a runtime surprise. |
| **Self-Documenting Code** | The enum values read as English and match the CBSE staff hierarchy directly. No magic numbers or string literals needed. |

---

### `model/ClassLevel.java` — Enum with Behaviour

**What it does:**
Defines the two class levels: `CLASS_10` and `CLASS_12`. Unlike `UserRole`, this enum carries data: each constant stores the numeric level (`10` or `12`), the grading type string (`"PHYSICAL"` or `"OSM"`), and a human-readable description. It also provides a `fromInt(int level)` factory method to convert a database integer back to the enum constant.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Enum with Fields and Methods** | Java enums are full classes. `ClassLevel` stores three fields per constant and exposes three getter methods. This is one of Java's most powerful OOP features — enums are not just named constants. |
| **Factory Method on Enum** | `ClassLevel.fromInt(10)` returns `CLASS_10`. This bridges the gap between the database (which stores `INT`) and the application (which works with the enum). |
| **Behaviour Delegation** | `Exam.getGradingType()` calls `classLevel.getGradingType()`. The grading type is defined once — in the enum — and reused everywhere. |

---

### `model/ScriptStatus.java` — Enum with Descriptions

**What it does:**
Defines the seven states in the script evaluation pipeline: `PENDING`, `AE_MARKED`, `HE_REVIEWED`, `COORDINATOR_VERIFIED`, `FINALIZED`, `UPLOADED`, `DISPUTED`. Each constant carries a human-readable description string displayed to users in the CLI.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Enum with Constructor** | Each constant is constructed with a description string. `UPLOADED("Uploaded to CBSE Central Portal")` means the string is always paired with the constant — they cannot get out of sync. |
| **State Machine Values** | Used as the values in `Script.canTransitionTo()`. The `switch` expression over `ScriptStatus` is exhaustive — the compiler enforces that all states are handled. |

---

## Layer 4 — Repository (Data Access)

### `repository/DataRepository.java`

**What it does:**
The single class that contains every SQL statement in the application. No service class ever writes SQL directly. This is the **DAO (Data Access Object) pattern** — a dedicated layer that translates between Java objects and database rows.

Covers all six tables with full CRUD where appropriate:
- **User operations**: find by email, find by roll number, find by ID, create, update last login, list students by class
- **Exam operations**: get upcoming exams, find by ID, create, get exams for a student
- **Script operations**: create, AE mark, HE review, coordinator finalize (with SERIALIZABLE transaction), upload, dispute, flag random 10% for HE review, fetch pending/review/coordinator lists
- **Enrollment operations**: enroll student, get enrollments
- **Audit log operations**: write, fetch recent logs
- **Statistics**: `getScriptStats()` returns counts by status in a single aggregate query

Every SQL statement uses `PreparedStatement` — parameterised queries that are immune to SQL injection.

The coordinator finalization method demonstrates full ACID compliance in code:
```
conn.setAutoCommit(false)
conn.setTransactionIsolation(TRANSACTION_SERIALIZABLE)
SELECT ... FOR UPDATE  ← acquires row-level lock
UPDATE scripts ...
conn.commit()
catch → conn.rollback()
```

The three private `mapUser()`, `mapExam()`, `mapScript()` methods convert `ResultSet` rows into model objects. All SQL-to-Java translation is centralized here.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **DAO Pattern** | All SQL is here. Services never touch `ResultSet`, `PreparedStatement`, or `Connection`. This means you could swap PostgreSQL for MySQL by changing only this file. |
| **Encapsulation** | The three `map*()` methods are `private`. They are implementation details — no external class needs to know how a `ResultSet` becomes a `User`. |
| **Single Responsibility** | `DataRepository` does one thing: talk to the database. It contains no business rules (no "if Class 10, validate sections" logic — that belongs in services). |
| **Try-with-Resources** | Every `Connection`, `PreparedStatement`, and `ResultSet` is opened inside a `try(...)` block. They are automatically closed even if an exception is thrown — prevents connection leaks. |
| **Dependency Injection** | `DataRepository` receives a `DatabaseManager` through its constructor rather than creating one. This makes it testable in isolation. |

---

## Layer 5 — Services (Business Logic)

### `service/AuthService.java`

**What it does:**
Manages the full authentication lifecycle: login, student registration, staff registration, and logout. On login, it attempts to find the user by email first, then by roll number (so both staff and students can log in with a single prompt). It verifies the BCrypt password hash, checks that the account is active, enforces a maximum of 5 failed attempts before locking, updates the `last_login` timestamp, and writes an audit log entry.

Registration methods validate password policy (minimum 8 characters, one uppercase, one digit, one special character), confirm the password is typed twice consistently, check for existing email/roll number conflicts, and write an audit entry on success.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Encapsulation of State** | `failedAttempts` and `loggedInUser` are private instance fields. No external class can reset the counter or change the logged-in user directly. |
| **Delegation** | Password hashing is delegated to `PasswordUtil.hash()`. Password verification is delegated to `PasswordUtil.verify()`. `AuthService` knows *when* to hash — not *how*. |
| **Fail-Fast Validation** | Registration checks for duplicate email/roll number before hashing the password, avoiding an expensive BCrypt operation on a request that will fail anyway. |
| **Audit Trail as a Cross-Cutting Concern** | Every path through `login()` — success, wrong password, account locked, user not found — writes to the audit log. The audit is never skipped. |

---

### `service/StudentPortal.java`

**What it does:**
The complete menu-driven interface for students after login. Displays five options and loops until the student logs out. Each option delegates to a private method:

1. **Exam schedule** — fetches all enrolled exams from the repository and renders them in a table with subject, date, timing, center, and marks breakdown
2. **Admit card** — renders a formatted admit card in the terminal with the student's name, roll number, class level, and CBSE instructions that differ by class (Class 12 gets OSM scanning guidance, Class 10 gets section marking guidance)
3. **Marking rules** — shows class-specific detailed marking guidelines. Class 10 gets section-by-section rules (MCQ, short answer, long answer, maps). Class 12 gets the five-step OSM pipeline explanation
4. **Script status** — explains where the student's script is in the pipeline without revealing marks (CBSE policy)
5. **Change password** — verifies current password, enforces policy on new password, confirms twice

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Encapsulation** | All five handler methods are `private`. The public API is just `showMenu()`. |
| **Polymorphism via Conditional Dispatch** | `viewMarkingRules()` calls either `showClass10Rules()` or `showClass12Rules()` based on `student.isClass10()`. The same method behaves completely differently depending on the student object's state. |
| **Delegation** | `StudentPortal` never touches SQL. It calls `repo.getExamsForStudent()` and works with the resulting list of `Exam` objects. |
| **Object State Driving Behaviour** | The admit card instructions, marking rules, and script status description all branch on `student.isClass12()` — a method on the `User` object. This is behaviour in the model driving the view. |

---

### `service/StaffPortal.java`

**What it does:**
The menu system for all five staff roles. The key challenge is that each role sees a completely different set of menu options — an Invigilator sees attendance tools, an AE sees marking sessions, a Coordinator sees finalization and upload. `StaffPortal` solves this with a role-based dispatch system:

- `printRoleMenu()` — uses a `switch` on `staff.getRole()` to print the correct menu for the logged-in role
- `getMenuMax()` — returns the highest valid menu option number for this role (prevents the user from typing an option that doesn't exist for their role)
- `handleChoice(int choice)` — dispatches to `handleInvigilator()`, `handleAE()`, `handleHE()`, `handleCoordinator()`, or `handleAdmin()` based on role

The staff portal also composes an `EvaluationEngine` internally — all marking, HE review, finalization, and upload actions are delegated to it.

**Admin-specific operations** (user registration, exam creation, enrollment) are implemented directly in `StaffPortal` since they require interaction and then write to the repository.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Polymorphism (role-based dispatch)** | `handleChoice()` dispatches entirely differently based on `staff.getRole()`. The same integer input `1` triggers duty schedule for an Invigilator, an AE marking session for an AE, an HE review session for an HE, and user registration for an Admin. This is runtime polymorphism without subclasses — achieved through enum dispatch. |
| **Composition** | `StaffPortal` owns an `EvaluationEngine` instance. Rather than duplicating marking logic, it delegates. This is composition: StaffPortal *has-a* EvaluationEngine. |
| **Access Control** | Every evaluation method checks the user's role at the top (`if (!currentUser.canMarkScripts())`) before doing anything. The model's behaviour method drives the service's access decision. |
| **Single Responsibility** | The portal handles menu display and user input only. All marking logic is in `EvaluationEngine`. All SQL is in `DataRepository`. |

---

### `service/EvaluationEngine.java`

**What it does:**
The most domain-rich service class. Implements the entire CBSE evaluation pipeline: script generation with anonymization, AE marking (with section-wise validation for Class 10 and OSM mode for Class 12), HE random 10% review (with variance checking), coordinator finalization, and portal upload simulation.

**Script generation:** Loops over all students enrolled in the exam. For Class 12 papers, calls `MaskIdGenerator.generate()` to produce a `BX-YYYY-XXXXXX` code. For Class 10, uses a `PHY-` prefix with the roll number. Each generated script is written to the database and audit-logged.

**AE marking:** Fetches scripts via a query that explicitly sets `student_id = 0` — the anonymization is enforced at the SQL level. For Class 10, prompts for marks in each section (A, B, C, D) and validates the total. If the section total exceeds the paper's total marks, the method calls itself recursively to re-prompt. For Class 12, prompts for a single total.

**HE flagging:** Calls the repository's random-10% SQL (`ORDER BY RANDOM() LIMIT count*0.1`). Then loads the flagged scripts and for each one, compares the entered HE marks against the existing AE marks. If the difference exceeds 5, warns the examiner and offers to flag the script as `DISPUTED`.

**Coordinator finalization:** Loads all AE-marked and HE-reviewed scripts, displays them in a table, and on confirmation runs the `coordinatorFinalize()` repository method (which internally uses `TRANSACTION_SERIALIZABLE` + `SELECT FOR UPDATE`). For HE-reviewed scripts, the HE marks become the effective final marks. For non-reviewed scripts, the AE marks are used.

**Portal upload simulation:** Iterates over `COORDINATOR_VERIFIED` scripts and calls `uploadScript()` for each, transitioning them to `UPLOADED`. In a production system this would POST a digitally-signed payload to cbseportal.nic.in.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Encapsulation of CBSE Policy** | The 5-mark variance threshold, the 10% HE sampling rule, the section-sum validation — all are in this class. If CBSE changes the variance threshold from 5 to 7, you change one line here. |
| **Recursion for Input Validation** | `markClass10Script()` calls itself if the section total is invalid. This is a clean recursive retry pattern — cleaner than a `while` loop with a flag variable. |
| **Delegation to Repository** | `EvaluationEngine` never writes SQL. All database calls go through `DataRepository`. The engine knows *what* to do; the repository knows *how* to persist it. |
| **Dependency Injection** | Receives both `DataRepository` and the current `User` through its constructor. Testable in isolation. |
| **Audit Trail Integration** | Every meaningful action — script generation, marks entry, HE review, finalization, upload — is audit-logged with enough detail for forensic review. |

---

## Layer 6 — Utilities

### `util/ConsoleUI.java`

**What it does:**
Centralises all terminal output and input. No other class calls `System.out.println()` directly — everything goes through `ConsoleUI`. This means if the application is ever moved to a web interface, only this class needs to change.

Provides: coloured output (`success()` in green, `error()` in red, `warn()` in yellow, `info()` in cyan), banner and section header printing, table rendering (takes a `String[]` headers and `String[][]` rows, computes column widths automatically), prompts (`prompt()`, `promptInt()` with range validation, `promptPassword()`, `promptYesNo()`), and a `pressEnterToContinue()` pause.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Utility Class Pattern** | `private ConsoleUI()` — the constructor is private so the class can never be instantiated. All methods are `static`. This is the standard Java utility class pattern (like `java.util.Collections`). |
| **`final` class** | Cannot be subclassed. The utility contract is fixed. |
| **Abstraction** | The rest of the application calls `ConsoleUI.success("Done")` without knowing anything about ANSI escape codes. The rendering details are completely hidden. |
| **Single Responsibility** | This class has one job: handle terminal I/O. It makes no business decisions and calls no other service. |
| **Separation of Concerns** | If the ANSI colour codes change or you want to strip colours for a log file, you change one class. Nothing else breaks. |

---

### `util/PasswordUtil.java`

**What it does:**
A stateless utility class with three methods: `hash(String)` — takes a plaintext password and returns a BCrypt hash with cost factor 12 (approximately 300ms to compute, making brute-force attacks impractical). `verify(String, String)` — takes a plaintext password and a stored hash and returns `true` if they match (BCrypt internally compares in constant time to prevent timing attacks). `meetsPolicy(String)` — checks that a password is at least 8 characters and contains at least one uppercase letter, one digit, and one special character.

BCrypt is the correct algorithm for password storage because it is slow by design (cost factor is adjustable as hardware speeds up), includes the salt in the output string, and is immune to rainbow table attacks.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Utility Class Pattern** | `private PasswordUtil()` — cannot be instantiated. All methods static. |
| **`final` class** | Cannot be subclassed — no one can create a `WeakPasswordUtil extends PasswordUtil` and accidentally use it somewhere. |
| **Single Responsibility** | This class knows about passwords only. It does not know about users, databases, or sessions. |
| **Defensive Programming** | `hash()` validates the input length before calling BCrypt, giving a clear `IllegalArgumentException` rather than letting BCrypt fail with a cryptic error. `verify()` handles `null` inputs safely by returning `false`. |

---

### `util/MaskIdGenerator.java`

**What it does:**
Generates the `BX-YYYY-XXXXXX` masked IDs used for Class 12 answer scripts. The format is: literal `BX-`, the current year (e.g. `2026`), a hyphen, then 6 characters drawn randomly from a custom alphabet.

The alphabet deliberately excludes `I`, `O`, `0`, and `1` to prevent visual confusion between letters and numbers when examiners read IDs on screen. The random source is `SecureRandom` — a cryptographically secure random number generator — rather than `Math.random()` or `java.util.Random`, which are predictable given enough observations.

**OOP Concepts:**

| Concept | Where |
|---|---|
| **Utility Class Pattern** | `private MaskIdGenerator()` — cannot be instantiated. |
| **`final` class** | Cannot be subclassed. |
| **Security-Conscious Design** | `SecureRandom` is a deliberate choice over `Random`. `Math.random()` is seeded from the system clock and is predictable. `SecureRandom` uses OS entropy sources and is cryptographically unpredictable — an examiner cannot guess another student's masked ID. |
| **Overloading** | Two `generate()` methods: one takes an explicit `int year`, one takes no arguments and uses `Year.now()`. The zero-argument version is a convenience overload. |
| **Constants** | `CHARS`, `TOKEN_LENGTH` are `private static final` — compile-time constants shared across all calls, not recreated on each invocation. |

---

## OOP Concepts — Master Reference

| Concept | Files Where It Appears | How It's Used |
|---|---|---|
| **Encapsulation** | All model classes, all service classes | Private fields, public methods, no direct field access from outside |
| **Abstraction** | `Main`, services, all callers of `DataRepository` | Services hide SQL; `ConsoleUI` hides ANSI codes; models hide internal state |
| **Polymorphism** | `Main.routeUser()`, `StaffPortal.handleChoice()`, `StudentPortal.viewMarkingRules()` | Runtime behaviour changes based on object state (`user.isStudent()`, `staff.getRole()`) |
| **Singleton Pattern** | `DatabaseManager`, `AppConfig` | One instance per JVM, `synchronized getInstance()`, private constructor |
| **Factory Method Pattern** | `User.newStudent()`, `User.newStaff()`, `ClassLevel.fromInt()` | Named constructors that make intent clear |
| **DAO Pattern** | `DataRepository` | All SQL in one class; services never touch JDBC |
| **State Machine Pattern** | `Script.canTransitionTo()`, `ScriptStatus` enum | Legal transitions explicitly listed; terminal state returns `false` |
| **Composition** | `StaffPortal` has-a `EvaluationEngine`; `EvaluationEngine` has-a `DataRepository` | Objects built from smaller objects, not deep inheritance |
| **Dependency Injection** | All service constructors | Dependencies passed in, not created internally; enables testability |
| **Utility Class Pattern** | `ConsoleUI`, `PasswordUtil`, `MaskIdGenerator` | Private constructor, all-static methods, `final` class |
| **Immutability** | `AuditLog` | All fields `final`, no setters, `final` class — models append-only audit policy |
| **Enums with Behaviour** | `ClassLevel` (fields + methods), `ScriptStatus` (description field) | Enums are full classes in Java; they carry data and methods |
| **Defensive Programming** | `PasswordUtil.hash()`, `PasswordUtil.verify()`, `AppConfig.get()` | Validate inputs early, fail fast with clear messages |
| **Recursion** | `EvaluationEngine.markClass10Script()` | Clean retry loop for invalid section totals |
| **Try-with-Resources** | `DataRepository` (all SQL methods) | Auto-close of `Connection`, `PreparedStatement`, `ResultSet` — no leaks |

---

## Why Layered Architecture Matters

```
┌─────────────────────────────────────┐
│   PRESENTATION   Main, ConsoleUI    │  ← User sees this
├─────────────────────────────────────┤
│   SERVICE        AuthService        │  ← Business rules live here
│                  StudentPortal      │
│                  StaffPortal        │
│                  EvaluationEngine   │
├─────────────────────────────────────┤
│   REPOSITORY     DataRepository     │  ← All SQL lives here
├─────────────────────────────────────┤
│   DATABASE       PostgreSQL         │  ← Data persists here
└─────────────────────────────────────┘
```

Each layer only talks to the layer directly below it. `Main` never calls SQL. Services never call `System.out`. The repository never makes business decisions. This means:

- You can swap PostgreSQL for MySQL by rewriting `DataRepository` alone.
- You can swap the CLI for a REST API by rewriting `Main` and the portals alone.
- You can change the 5-mark variance rule by editing one line in `EvaluationEngine` alone.

That is the practical value of OOP applied to architecture — **change in one place, no breakage elsewhere**.

---

*CBSE EEMS 2026 | Java 21 | PostgreSQL 16 | HikariCP | BCrypt*
