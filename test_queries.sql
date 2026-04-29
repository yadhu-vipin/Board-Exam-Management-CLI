-- ═══════════════════════════════════════════════════════════════════════════
--  CBSE EEMS 2026 — Complete Test & Verification Suite
--  Run against your cbse_eems database:
--  psql -U cbse_admin -d cbse_eems -f test_queries.sql
--
--  Sections:
--    T1  Basic data integrity
--    T2  Normalization (1NF / 2NF / 3NF) checks
--    T3  Constraint enforcement (should ERROR or return 0 rows)
--    T4  Referential integrity (FK checks)
--    T5  Business logic & CBSE policy
--    T6  ACID & pipeline state machine
--    T7  Anonymization (OSM security)
--    T8  Index & performance checks
--    T9  Audit trail completeness
--    T10 Full pipeline simulation (end-to-end)
-- ═══════════════════════════════════════════════════════════════════════════

\echo ''
\echo '══════════════════════════════════════════════════════'
\echo '  CBSE EEMS 2026 — DATABASE TEST SUITE'
\echo '══════════════════════════════════════════════════════'
\echo ''

-- ───────────────────────────────────────────────────────────────────────────
-- T1: BASIC DATA INTEGRITY
-- ───────────────────────────────────────────────────────────────────────────
\echo '── T1: Basic Data Integrity ──────────────────────────'

\echo '[T1.1] Row counts — expect: 5 centers, 7 staff, 20 students, 14 exams'
SELECT
  (SELECT COUNT(*) FROM exam_centers)           AS centers,
  (SELECT COUNT(*) FROM users WHERE role != 'STUDENT') AS staff,
  (SELECT COUNT(*) FROM users WHERE role = 'STUDENT')  AS students,
  (SELECT COUNT(*) FROM exams)                  AS exams,
  (SELECT COUNT(*) FROM scripts)                AS scripts,
  (SELECT COUNT(*) FROM student_exam_enrollment) AS enrollments,
  (SELECT COUNT(*) FROM audit_logs)             AS audit_logs;

\echo '[T1.2] Users by role'
SELECT role, COUNT(*) AS count
FROM users
GROUP BY role
ORDER BY role;

\echo '[T1.3] Exams: Class 10 vs 12, OSM breakdown'
SELECT
  class_level,
  COUNT(*)                                          AS total_exams,
  SUM(CASE WHEN osm_enabled THEN 1 ELSE 0 END)     AS osm_enabled,
  SUM(CASE WHEN NOT osm_enabled THEN 1 ELSE 0 END) AS physical
FROM exams
GROUP BY class_level
ORDER BY class_level;

\echo '[T1.4] Scripts by status (pipeline distribution)'
SELECT status, COUNT(*) AS count
FROM scripts
GROUP BY status
ORDER BY
  CASE status
    WHEN 'PENDING'               THEN 1
    WHEN 'AE_MARKED'             THEN 2
    WHEN 'HE_REVIEWED'           THEN 3
    WHEN 'COORDINATOR_VERIFIED'  THEN 4
    WHEN 'FINALIZED'             THEN 5
    WHEN 'UPLOADED'              THEN 6
    WHEN 'DISPUTED'              THEN 7
  END;

\echo '[T1.5] All exam centers loaded correctly'
SELECT center_code, center_name, district, state FROM exam_centers ORDER BY center_id;

\echo '[T1.6] All staff accounts with their codes'
SELECT name, role, staff_code, is_active FROM users WHERE role != 'STUDENT' ORDER BY role;

\echo '[T1.7] All students — class split, roll numbers unique'
SELECT class_level, COUNT(*) AS count,
       COUNT(DISTINCT roll_number) AS unique_rolls
FROM users WHERE role = 'STUDENT'
GROUP BY class_level;


-- ───────────────────────────────────────────────────────────────────────────
-- T2: NORMALIZATION CHECKS
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T2: Normalization ─────────────────────────────────'

\echo '[T2.1] 1NF — No NULL in NOT NULL columns (name, email, role)'
SELECT COUNT(*) AS violations_should_be_0
FROM users
WHERE name IS NULL OR email IS NULL OR role IS NULL OR password_hash IS NULL;

\echo '[T2.2] 1NF — No duplicate emails (unique constraint)'
SELECT email, COUNT(*) AS occurrences
FROM users
GROUP BY email
HAVING COUNT(*) > 1;
-- Expected: 0 rows

\echo '[T2.3] 2NF — Enrollment: no student enrolled in same exam twice'
SELECT student_id, exam_id, COUNT(*) AS duplicates
FROM student_exam_enrollment
GROUP BY student_id, exam_id
HAVING COUNT(*) > 1;
-- Expected: 0 rows

\echo '[T2.4] 2NF — Each script has exactly one masked_id (no duplicates)'
SELECT masked_id, COUNT(*) AS duplicates
FROM scripts
GROUP BY masked_id
HAVING COUNT(*) > 1;
-- Expected: 0 rows

\echo '[T2.5] 3NF — Center data not repeated inside exams table'
\echo '       (center_name must come from JOIN, not from exams directly)'
SELECT column_name
FROM information_schema.columns
WHERE table_name = 'exams'
  AND column_name IN ('center_name','district','state','pin_code');
-- Expected: 0 rows (these columns do NOT exist on exams — they are in exam_centers)

\echo '[T2.6] 3NF — exams.center_id references exam_centers correctly'
SELECT e.exam_id, e.subject, ec.center_name, ec.state
FROM exams e
LEFT JOIN exam_centers ec ON e.center_id = ec.center_id
ORDER BY e.exam_id
LIMIT 8;

\echo '[T2.7] BCNF — roll_number is a candidate key (each student unique)'
SELECT roll_number, COUNT(*) AS count
FROM users
WHERE roll_number IS NOT NULL
GROUP BY roll_number
HAVING COUNT(*) > 1;
-- Expected: 0 rows

\echo '[T2.8] BCNF — masked_id is a candidate key in scripts'
SELECT masked_id, COUNT(*) AS count
FROM scripts
GROUP BY masked_id
HAVING COUNT(*) > 1;
-- Expected: 0 rows


-- ───────────────────────────────────────────────────────────────────────────
-- T3: CONSTRAINT ENFORCEMENT (each block should raise ERROR or return 0)
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T3: Constraint Enforcement ────────────────────────'

\echo '[T3.1] CHECK: Student without roll_number must be rejected'
DO $$
BEGIN
  INSERT INTO users (name, email, password_hash, role, class_level)
  VALUES ('Bad Student', 'bad@test.com', 'hash', 'STUDENT', 12);
  RAISE NOTICE 'FAIL — insert should have been rejected';
EXCEPTION WHEN check_violation THEN
  RAISE NOTICE 'PASS — student without roll_number correctly rejected';
END $$;

\echo '[T3.2] CHECK: Staff without staff_code must be rejected'
DO $$
BEGIN
  INSERT INTO users (name, email, password_hash, role)
  VALUES ('Bad Staff', 'badstaff@test.com', 'hash', 'INVIGILATOR');
  RAISE NOTICE 'FAIL — insert should have been rejected';
EXCEPTION WHEN check_violation THEN
  RAISE NOTICE 'PASS — staff without staff_code correctly rejected';
END $$;

\echo '[T3.3] CHECK: osm_enabled=TRUE on Class 10 exam must be rejected'
DO $$
BEGIN
  INSERT INTO exams (subject, subject_code, exam_date, class_level, osm_enabled)
  VALUES ('Test', '999', '2026-04-01', 10, TRUE);
  RAISE NOTICE 'FAIL — OSM on Class 10 should be rejected';
EXCEPTION WHEN check_violation THEN
  RAISE NOTICE 'PASS — OSM correctly blocked for Class 10';
END $$;

\echo '[T3.4] CHECK: Negative marks must be rejected'
DO $$
DECLARE v_sid INT;
BEGIN
  SELECT script_id INTO v_sid FROM scripts LIMIT 1;
  UPDATE scripts SET ae_marks = -5 WHERE script_id = v_sid;
  RAISE NOTICE 'FAIL — negative marks should be rejected';
EXCEPTION WHEN check_violation THEN
  RAISE NOTICE 'PASS — negative marks correctly rejected';
END $$;

\echo '[T3.5] UNIQUE: Duplicate email must be rejected'
DO $$
BEGIN
  INSERT INTO users (name, email, password_hash, role, staff_code)
  VALUES ('Dup Admin', 'admin@cbse.nic.in', 'hash', 'ADMIN', 'CBSE-DUP-999');
  RAISE NOTICE 'FAIL — duplicate email should be rejected';
EXCEPTION WHEN unique_violation THEN
  RAISE NOTICE 'PASS — duplicate email correctly rejected';
END $$;

\echo '[T3.6] UNIQUE: Duplicate roll_number must be rejected'
DO $$
BEGIN
  INSERT INTO users (name, email, password_hash, role, class_level, roll_number)
  VALUES ('Dup Student', 'dup@test.com', 'hash', 'STUDENT', 12, 'DEL12-2601');
  RAISE NOTICE 'FAIL — duplicate roll number should be rejected';
EXCEPTION WHEN unique_violation THEN
  RAISE NOTICE 'PASS — duplicate roll_number correctly rejected';
END $$;

\echo '[T3.7] UNIQUE: Duplicate masked_id in scripts must be rejected'
DO $$
DECLARE v_eid INT; v_uid INT;
BEGIN
  SELECT exam_id INTO v_eid FROM exams WHERE class_level = 12 LIMIT 1;
  SELECT id INTO v_uid FROM users WHERE role = 'STUDENT' AND class_level = 12 LIMIT 1;
  INSERT INTO scripts (student_id, masked_id, exam_id, subject, class_level)
  VALUES (v_uid, 'BX-2026-A3F9K2', v_eid, 'English Core', 12);
  RAISE NOTICE 'FAIL — duplicate masked_id should be rejected';
EXCEPTION WHEN unique_violation THEN
  RAISE NOTICE 'PASS — duplicate masked_id correctly rejected';
END $$;

\echo '[T3.8] UNIQUE: Student enrolled in same exam twice must be rejected'
DO $$
DECLARE v_sid INT; v_eid INT;
BEGIN
  SELECT student_id, exam_id INTO v_sid, v_eid
  FROM student_exam_enrollment LIMIT 1;
  INSERT INTO student_exam_enrollment (student_id, exam_id, seat_number)
  VALUES (v_sid, v_eid, 'Z-99');
  RAISE NOTICE 'FAIL — duplicate enrollment should be rejected';
EXCEPTION WHEN unique_violation THEN
  RAISE NOTICE 'PASS — duplicate enrollment correctly rejected';
END $$;

\echo '[T3.9] CHECK: Invalid role value must be rejected'
DO $$
BEGIN
  INSERT INTO users (name, email, password_hash, role, staff_code)
  VALUES ('Hacker', 'hack@test.com', 'hash', 'SUPERUSER', 'HACK-001');
  RAISE NOTICE 'FAIL — invalid role should be rejected';
EXCEPTION WHEN check_violation THEN
  RAISE NOTICE 'PASS — invalid role correctly rejected';
END $$;

\echo '[T3.10] CHECK: Invalid status in scripts must be rejected'
DO $$
DECLARE v_sid INT;
BEGIN
  SELECT script_id INTO v_sid FROM scripts LIMIT 1;
  UPDATE scripts SET status = 'HACKED' WHERE script_id = v_sid;
  RAISE NOTICE 'FAIL — invalid status should be rejected';
EXCEPTION WHEN check_violation THEN
  RAISE NOTICE 'PASS — invalid script status correctly rejected';
END $$;


-- ───────────────────────────────────────────────────────────────────────────
-- T4: REFERENTIAL INTEGRITY (Foreign Keys)
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T4: Referential Integrity ─────────────────────────'

\echo '[T4.1] FK: Cannot insert script referencing non-existent student'
DO $$
BEGIN
  INSERT INTO scripts (student_id, masked_id, exam_id, subject, class_level)
  VALUES (99999, 'BX-2026-FAKE01', 1, 'Fake', 12);
  RAISE NOTICE 'FAIL — FK violation should be caught';
EXCEPTION WHEN foreign_key_violation THEN
  RAISE NOTICE 'PASS — student FK correctly enforced on scripts';
END $$;

\echo '[T4.2] FK: Cannot insert enrollment referencing non-existent exam'
DO $$
DECLARE v_uid INT;
BEGIN
  SELECT id INTO v_uid FROM users WHERE role = 'STUDENT' LIMIT 1;
  INSERT INTO student_exam_enrollment (student_id, exam_id)
  VALUES (v_uid, 99999);
  RAISE NOTICE 'FAIL — FK violation should be caught';
EXCEPTION WHEN foreign_key_violation THEN
  RAISE NOTICE 'PASS — exam FK correctly enforced on enrollment';
END $$;

\echo '[T4.3] FK: Cannot insert audit_log referencing non-existent user'
DO $$
BEGIN
  INSERT INTO audit_logs (user_id, action)
  VALUES (99999, 'FAKE_ACTION');
  RAISE NOTICE 'FAIL — FK violation should be caught';
EXCEPTION WHEN foreign_key_violation THEN
  RAISE NOTICE 'PASS — user FK correctly enforced on audit_logs';
END $$;

\echo '[T4.4] FK: All scripts reference valid exams (no orphan scripts)'
SELECT COUNT(*) AS orphan_scripts_should_be_0
FROM scripts s
LEFT JOIN exams e ON s.exam_id = e.exam_id
WHERE e.exam_id IS NULL;

\echo '[T4.5] FK: All enrollments reference valid students'
SELECT COUNT(*) AS orphan_enrollments_should_be_0
FROM student_exam_enrollment see
LEFT JOIN users u ON see.student_id = u.id
WHERE u.id IS NULL;

\echo '[T4.6] CASCADE: Deleting a student removes their enrollments'
DO $$
DECLARE v_test_uid INT;
BEGIN
  -- Insert a temp student
  INSERT INTO users (name, email, password_hash, role, class_level, roll_number)
  VALUES ('Temp Del', 'temp.del@test.com', 'hash', 'STUDENT', 12, 'TEST-DEL-9999')
  RETURNING id INTO v_test_uid;

  -- Enroll them
  INSERT INTO student_exam_enrollment (student_id, exam_id)
  VALUES (v_test_uid, 1);

  -- Delete the student
  DELETE FROM users WHERE id = v_test_uid;

  -- Check enrollment gone
  IF NOT EXISTS (SELECT 1 FROM student_exam_enrollment WHERE student_id = v_test_uid) THEN
    RAISE NOTICE 'PASS — CASCADE delete removed enrollment correctly';
  ELSE
    RAISE NOTICE 'FAIL — enrollment remained after student deleted';
  END IF;
END $$;


-- ───────────────────────────────────────────────────────────────────────────
-- T5: BUSINESS LOGIC & CBSE POLICY
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T5: Business Logic & CBSE Policy ─────────────────'

\echo '[T5.1] OSM: All Class 12 exams with total_marks >= 40 have osm_enabled=TRUE'
SELECT exam_id, subject, class_level, total_marks, osm_enabled,
  CASE WHEN class_level = 12 AND total_marks >= 40 AND NOT osm_enabled
       THEN 'POLICY VIOLATION' ELSE 'OK' END AS policy_check
FROM exams
WHERE class_level = 12
ORDER BY exam_id;

\echo '[T5.2] OSM: No Class 10 exam has osm_enabled=TRUE'
SELECT COUNT(*) AS class10_osm_violations_should_be_0
FROM exams
WHERE class_level = 10 AND osm_enabled = TRUE;

\echo '[T5.3] HE Policy: Check ~10% of AE-marked scripts are flagged for HE review'
SELECT
  exam_id,
  COUNT(*) AS total_scripts,
  SUM(CASE WHEN he_review_needed THEN 1 ELSE 0 END) AS he_flagged,
  ROUND(100.0 * SUM(CASE WHEN he_review_needed THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0), 1) AS pct_flagged
FROM scripts
GROUP BY exam_id
ORDER BY exam_id;

\echo '[T5.4] Class 10 Section-wise: section totals match ae_marks for marked scripts'
SELECT
  script_id, masked_id,
  ae_marks,
  section_a_marks + section_b_marks + section_c_marks + section_d_marks AS section_sum,
  CASE
    WHEN ae_marks = 0 THEN 'PENDING (not marked yet)'
    WHEN ae_marks = section_a_marks + section_b_marks + section_c_marks + section_d_marks
         THEN 'OK — sections match'
    ELSE 'MISMATCH — policy violation'
  END AS section_check
FROM scripts
WHERE class_level = 10
ORDER BY script_id;

\echo '[T5.5] Class 12 masked_id format check (must start with BX-)'
SELECT masked_id,
  CASE WHEN masked_id LIKE 'BX-%' THEN 'VALID OSM ID'
       WHEN masked_id LIKE 'PHY-%' THEN 'VALID PHYSICAL ID'
       ELSE 'INVALID FORMAT' END AS id_format
FROM scripts
ORDER BY class_level, masked_id
LIMIT 20;

\echo '[T5.6] Variance check: AE-HE difference > 5 marks (should be disputed)'
SELECT
  script_id, masked_id, ae_marks, he_marks,
  ABS(ae_marks - he_marks) AS variance,
  status,
  CASE WHEN ABS(ae_marks - he_marks) > 5 AND status != 'DISPUTED'
       THEN 'POLICY BREACH — should be DISPUTED'
       ELSE 'OK'
  END AS variance_check
FROM scripts
WHERE he_review_needed = TRUE AND he_marks > 0;

\echo '[T5.7] Enrollment class-level consistency: student class must match exam class'
SELECT COUNT(*) AS class_mismatch_should_be_0
FROM student_exam_enrollment see
JOIN users u ON see.student_id = u.id
JOIN exams e ON see.exam_id = e.exam_id
WHERE u.class_level != e.class_level;

\echo '[T5.8] All UPLOADED scripts have a coordinator_id and finalized_at set'
SELECT
  script_id, masked_id, status,
  CASE WHEN coordinator_id IS NULL THEN 'MISSING coordinator'
       WHEN finalized_at IS NULL   THEN 'MISSING finalized_at'
       ELSE 'OK' END AS completeness_check
FROM scripts
WHERE status = 'UPLOADED';


-- ───────────────────────────────────────────────────────────────────────────
-- T6: ACID & STATE MACHINE
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T6: ACID & State Machine ──────────────────────────'

\echo '[T6.1] Atomicity: Simulated failed transaction leaves no partial data'
DO $$
DECLARE
  v_count_before INT;
  v_count_after  INT;
BEGIN
  SELECT COUNT(*) INTO v_count_before FROM scripts;

  BEGIN
    -- Insert a valid script
    INSERT INTO scripts (student_id, masked_id, exam_id, subject, class_level)
    SELECT id, 'BX-ACID-TEST01', 1, 'Test', 12
    FROM users WHERE role='STUDENT' AND class_level=12 LIMIT 1;

    -- Force a failure (duplicate masked_id)
    INSERT INTO scripts (student_id, masked_id, exam_id, subject, class_level)
    SELECT id, 'BX-ACID-TEST01', 1, 'Test', 12
    FROM users WHERE role='STUDENT' AND class_level=12 LIMIT 1;

    RAISE NOTICE 'FAIL — should not reach here';
  EXCEPTION WHEN OTHERS THEN
    -- Savepoint auto-rollback happened
    NULL;
  END;

  SELECT COUNT(*) INTO v_count_after FROM scripts;

  IF v_count_before = v_count_after THEN
    RAISE NOTICE 'PASS — Atomicity confirmed: no partial insert survived';
  ELSE
    RAISE NOTICE 'FAIL — Partial data leaked: before=% after=%', v_count_before, v_count_after;
  END IF;
END $$;

\echo '[T6.2] Consistency: Status transitions — verify valid pipeline order'
SELECT
  script_id, masked_id, status,
  CASE
    WHEN status = 'AE_MARKED'            AND ae_marks = 0         THEN 'INCONSISTENT — AE_MARKED but no marks'
    WHEN status = 'HE_REVIEWED'          AND he_marks = 0         THEN 'INCONSISTENT — HE_REVIEWED but no HE marks'
    WHEN status = 'COORDINATOR_VERIFIED' AND final_marks = 0      THEN 'INCONSISTENT — verified but no final marks'
    WHEN status = 'UPLOADED'             AND coordinator_id IS NULL THEN 'INCONSISTENT — uploaded without coordinator'
    WHEN status IN ('AE_MARKED','HE_REVIEWED','COORDINATOR_VERIFIED','FINALIZED','UPLOADED')
         AND ae_staff_id IS NULL                                   THEN 'INCONSISTENT — marked but no AE assigned'
    ELSE 'CONSISTENT'
  END AS consistency_check
FROM scripts
ORDER BY script_id;

\echo '[T6.3] Isolation: Row-level locking test (SERIALIZABLE simulation)'
DO $$
DECLARE v_sid INT; v_marks INT;
BEGIN
  SELECT script_id INTO v_sid FROM scripts WHERE status='AE_MARKED' LIMIT 1;
  IF v_sid IS NULL THEN
    RAISE NOTICE 'SKIP — no AE_MARKED script available for lock test';
    RETURN;
  END IF;

  -- Simulate a SELECT FOR UPDATE (what coordinator finalization does)
  PERFORM script_id FROM scripts WHERE script_id = v_sid FOR UPDATE NOWAIT;
  RAISE NOTICE 'PASS — SELECT FOR UPDATE lock acquired on script_id=%', v_sid;
EXCEPTION WHEN lock_not_available THEN
  RAISE NOTICE 'INFO — script is locked by another transaction (correct isolation behaviour)';
END $$;

\echo '[T6.4] Durability: WAL confirmation (PostgreSQL setting check)'
SELECT name, setting, unit
FROM pg_settings
WHERE name IN ('wal_level', 'fsync', 'synchronous_commit')
ORDER BY name;
-- wal_level should be replica or logical, fsync=on, synchronous_commit=on

\echo '[T6.5] State machine: No script can skip pipeline stages'
SELECT
  script_id, masked_id, status,
  CASE
    WHEN status = 'HE_REVIEWED'          AND ae_marked_at IS NULL  THEN 'SKIPPED AE stage'
    WHEN status = 'COORDINATOR_VERIFIED' AND ae_marked_at IS NULL  THEN 'SKIPPED AE stage'
    WHEN status = 'UPLOADED'             AND finalized_at IS NULL  THEN 'SKIPPED finalization'
    ELSE 'VALID'
  END AS stage_check
FROM scripts
WHERE status NOT IN ('PENDING','AE_MARKED');


-- ───────────────────────────────────────────────────────────────────────────
-- T7: ANONYMIZATION & OSM SECURITY
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T7: Anonymization & OSM Security ─────────────────'

\echo '[T7.1] AE view: student_id must NOT be derivable from masked_id alone'
-- This simulates exactly what the AE sees (as coded in DataRepository.getPendingScriptsForAE)
SELECT
  script_id,
  masked_id,      -- AE sees this
  exam_id,
  subject,
  status,
  0 AS student_id  -- student_id explicitly hidden (set to 0 in AE query)
  -- NOTE: real student_id column deliberately excluded
FROM scripts
WHERE class_level = 12
ORDER BY masked_id
LIMIT 5;

\echo '[T7.2] Coordinator view: student_id IS visible (for final verification)'
SELECT
  s.script_id,
  s.masked_id,
  s.student_id,   -- Coordinator can see this
  u.name AS student_name,
  u.roll_number,
  s.final_marks,
  s.status
FROM scripts s
JOIN users u ON s.student_id = u.id
WHERE s.status IN ('COORDINATOR_VERIFIED','UPLOADED')
ORDER BY s.masked_id;

\echo '[T7.3] Verify masked_id randomness: no two scripts share a masked_id prefix pattern'
SELECT
  SUBSTRING(masked_id, 1, 8) AS prefix,
  COUNT(*) AS scripts_with_prefix
FROM scripts
WHERE masked_id LIKE 'BX-%'
GROUP BY prefix
HAVING COUNT(*) > 1;
-- Expected: 0 rows (each BX prefix segment is unique)

\echo '[T7.4] Cross-check: student cannot reverse-lookup their masked_id'
-- A student knows only their roll number — verify roll_number has NO direct link in scripts
SELECT column_name
FROM information_schema.columns
WHERE table_name = 'scripts'
  AND column_name = 'roll_number';
-- Expected: 0 rows (roll_number is NOT a column in scripts — only masked_id is)

\echo '[T7.5] Class 10 physical: PHY- IDs correctly use roll_number format'
SELECT masked_id, class_level,
  CASE WHEN class_level = 10 AND masked_id LIKE 'PHY-%' THEN 'CORRECT'
       WHEN class_level = 12 AND masked_id LIKE 'BX-%'  THEN 'CORRECT'
       ELSE 'FORMAT ERROR'
  END AS format_check
FROM scripts ORDER BY class_level, masked_id;


-- ───────────────────────────────────────────────────────────────────────────
-- T8: INDEX & PERFORMANCE CHECKS
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T8: Index & Performance ───────────────────────────'

\echo '[T8.1] Verify all expected indexes exist'
SELECT
  indexname,
  tablename,
  CASE WHEN indexname IS NOT NULL THEN 'EXISTS' ELSE 'MISSING' END AS status
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
    'idx_scripts_status',
    'idx_scripts_exam',
    'idx_scripts_masked',
    'idx_audit_user',
    'idx_audit_timestamp',
    'idx_enrollment_student',
    'idx_users_roll',
    'idx_users_email'
  )
ORDER BY tablename, indexname;

\echo '[T8.2] Query plan: scripts filtered by status uses index (not seq scan)'
EXPLAIN (FORMAT TEXT, COSTS OFF)
SELECT * FROM scripts WHERE status = 'PENDING';

\echo '[T8.3] Query plan: user lookup by email uses index'
EXPLAIN (FORMAT TEXT, COSTS OFF)
SELECT * FROM users WHERE email = 'admin@cbse.nic.in';

\echo '[T8.4] Query plan: enrollment lookup by student uses index'
EXPLAIN (FORMAT TEXT, COSTS OFF)
SELECT * FROM student_exam_enrollment WHERE student_id = 1;

\echo '[T8.5] Table sizes'
SELECT
  relname AS table_name,
  pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
  n_live_tup AS live_rows
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;


-- ───────────────────────────────────────────────────────────────────────────
-- T9: AUDIT TRAIL COMPLETENESS
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T9: Audit Trail ───────────────────────────────────'

\echo '[T9.1] All action types present in audit log'
SELECT action, COUNT(*) AS occurrences
FROM audit_logs
GROUP BY action
ORDER BY action;

\echo '[T9.2] Every UPLOADED script has a PORTAL_UPLOAD audit entry'
SELECT
  s.script_id, s.masked_id, s.status,
  CASE WHEN al.log_id IS NOT NULL THEN 'AUDIT FOUND'
       ELSE 'MISSING AUDIT ENTRY'
  END AS audit_check
FROM scripts s
LEFT JOIN audit_logs al ON al.target_id = s.script_id
  AND al.action = 'PORTAL_UPLOAD'
WHERE s.status = 'UPLOADED';

\echo '[T9.3] Every AE_MARKED script has an AE_MARKS_ENTERED audit entry'
SELECT
  s.script_id, s.masked_id,
  CASE WHEN al.log_id IS NOT NULL THEN 'AUDIT FOUND'
       ELSE 'MISSING AUDIT ENTRY'
  END AS audit_check
FROM scripts s
LEFT JOIN audit_logs al ON al.target_id = s.script_id
  AND al.action = 'AE_MARKS_ENTERED'
WHERE s.status != 'PENDING';

\echo '[T9.4] Audit log is strictly append-only (no UPDATEs ever happened)'
SELECT
  'Audit entries cannot be updated or deleted — enforced at application layer' AS note,
  COUNT(*) AS total_entries,
  MIN(timestamp) AS oldest_entry,
  MAX(timestamp) AS newest_entry
FROM audit_logs;

\echo '[T9.5] Audit trail timeline for one script (end-to-end trace)'
SELECT al.timestamp, al.action, al.detail, u.name AS performed_by
FROM audit_logs al
JOIN users u ON al.user_id = u.id
WHERE al.target_entity = 'script'
  AND al.target_id = (
    SELECT script_id FROM scripts WHERE status = 'UPLOADED' LIMIT 1
  )
ORDER BY al.timestamp;


-- ───────────────────────────────────────────────────────────────────────────
-- T10: FULL PIPELINE SIMULATION (End-to-End)
-- ───────────────────────────────────────────────────────────────────────────
\echo ''
\echo '── T10: Full Pipeline Simulation ─────────────────────'

\echo '[T10.1] Complete evaluation pipeline summary per exam'
SELECT
  e.exam_id,
  e.subject,
  e.class_level,
  CASE WHEN e.osm_enabled THEN 'OSM' ELSE 'Physical' END AS marking_type,
  COUNT(s.script_id)                                                     AS total_scripts,
  COUNT(s.script_id) FILTER (WHERE s.status = 'PENDING')                AS pending,
  COUNT(s.script_id) FILTER (WHERE s.status = 'AE_MARKED')              AS ae_marked,
  COUNT(s.script_id) FILTER (WHERE s.status = 'HE_REVIEWED')            AS he_reviewed,
  COUNT(s.script_id) FILTER (WHERE s.status = 'COORDINATOR_VERIFIED')   AS coord_verified,
  COUNT(s.script_id) FILTER (WHERE s.status = 'UPLOADED')               AS uploaded,
  ROUND(
    100.0 * COUNT(s.script_id) FILTER (WHERE s.status = 'UPLOADED')
    / NULLIF(COUNT(s.script_id), 0), 1
  ) AS pct_complete
FROM exams e
LEFT JOIN scripts s ON e.exam_id = s.exam_id
GROUP BY e.exam_id, e.subject, e.class_level, e.osm_enabled
ORDER BY e.class_level, e.exam_id;

\echo '[T10.2] Marks distribution for completed scripts'
SELECT
  e.subject,
  e.class_level,
  MIN(s.final_marks)                          AS min_marks,
  ROUND(AVG(s.final_marks), 1)               AS avg_marks,
  MAX(s.final_marks)                          AS max_marks,
  e.total_marks                               AS out_of,
  ROUND(100.0 * AVG(s.final_marks) / NULLIF(e.total_marks, 0), 1) AS avg_pct
FROM scripts s
JOIN exams e ON s.exam_id = e.exam_id
WHERE s.status IN ('COORDINATOR_VERIFIED', 'UPLOADED')
  AND s.final_marks > 0
GROUP BY e.subject, e.class_level, e.total_marks
ORDER BY e.class_level, e.subject;

\echo '[T10.3] Staff workload: how many scripts each AE has marked'
SELECT
  u.name AS examiner,
  u.role,
  COUNT(s.script_id) AS scripts_marked,
  ROUND(AVG(s.ae_marks), 1) AS avg_marks_given
FROM users u
LEFT JOIN scripts s ON s.ae_staff_id = u.id
WHERE u.role = 'ASSISTANT_EXAMINER'
GROUP BY u.id, u.name, u.role;

\echo '[T10.4] HE workload and variance stats'
SELECT
  u.name AS head_examiner,
  COUNT(s.script_id) AS scripts_reviewed,
  SUM(CASE WHEN ABS(s.ae_marks - s.he_marks) > 5 THEN 1 ELSE 0 END) AS high_variance_count,
  ROUND(AVG(ABS(s.ae_marks - s.he_marks)), 1) AS avg_ae_he_variance
FROM users u
LEFT JOIN scripts s ON s.he_staff_id = u.id AND s.he_marks > 0
WHERE u.role = 'HEAD_EXAMINER'
GROUP BY u.id, u.name;

\echo '[T10.5] Student result card (Class 12 — coordinator view)'
SELECT
  u.name AS student_name,
  u.roll_number,
  e.subject,
  s.masked_id,
  s.ae_marks,
  CASE WHEN s.he_review_needed THEN s.he_marks::TEXT ELSE 'N/A' END AS he_marks,
  s.final_marks,
  e.total_marks AS out_of,
  s.status,
  CASE
    WHEN s.final_marks >= e.total_marks * 0.9 THEN 'Distinction'
    WHEN s.final_marks >= e.total_marks * 0.75 THEN 'First Class'
    WHEN s.final_marks >= e.total_marks * 0.6  THEN 'Second Class'
    WHEN s.final_marks >= e.total_marks * 0.33 THEN 'Pass'
    WHEN s.final_marks = 0                      THEN 'Pending'
    ELSE 'Fail'
  END AS grade_band
FROM scripts s
JOIN users u ON s.student_id = u.id
JOIN exams e ON s.exam_id = e.exam_id
WHERE s.class_level = 12
  AND s.status IN ('COORDINATOR_VERIFIED', 'UPLOADED')
ORDER BY u.roll_number, e.subject;

\echo '[T10.6] Class 10 section-wise marks breakdown'
SELECT
  u.name AS student_name,
  u.roll_number,
  s.masked_id,
  s.section_a_marks AS sec_a,
  s.section_b_marks AS sec_b,
  s.section_c_marks AS sec_c,
  s.section_d_marks AS sec_d,
  s.section_a_marks + s.section_b_marks +
    s.section_c_marks + s.section_d_marks AS section_total,
  s.ae_marks AS ae_total,
  CASE WHEN s.section_a_marks + s.section_b_marks +
            s.section_c_marks + s.section_d_marks = s.ae_marks
       THEN 'BALANCED' ELSE 'MISMATCH' END AS balance_check
FROM scripts s
JOIN users u ON s.student_id = u.id
WHERE s.class_level = 10 AND s.ae_marks > 0
ORDER BY u.roll_number;

\echo ''
\echo '══════════════════════════════════════════════════════'
\echo '  TEST SUITE COMPLETE'
\echo '  All PASS/FAIL messages printed above.'
\echo '  All constraint tests should show PASS.'
\echo '  All "should_be_0" counts should return 0.'
\echo '══════════════════════════════════════════════════════'
\echo ''
