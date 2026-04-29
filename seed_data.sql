-- ═══════════════════════════════════════════════════════════════════════════
--  CBSE EEMS 2026 — Complete Seed Data
--  Run this against your cbse_eems database AFTER the schema is created.
--  psql -U cbse_admin -d cbse_eems -f seed_data.sql
--
--  DEFAULT PASSWORDS (change in production!):
--    admin@cbse.nic.in          → Admin@2026!
--    coordinator@cbse.nic.in    → Coord@2026!
--    he@cbse.nic.in             → HeExam@2026!
--    ae@cbse.nic.in             → AeExam@2026!
--    invigilator@cbse.nic.in    → Invig@2026!
--    all students               → Student@2026!
-- ═══════════════════════════════════════════════════════════════════════════

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────
-- 1. EXAM CENTERS (3 centers across India)
-- ─────────────────────────────────────────────────────────────────────────
INSERT INTO exam_centers (center_code, center_name, district, state, pin_code) VALUES
  ('DEL-0001', 'Kendriya Vidyalaya No. 1, Andrews Ganj', 'New Delhi',        'Delhi',       '110049'),
  ('MUM-0021', 'DAV Public School, Thane West',           'Thane',            'Maharashtra', '400601'),
  ('CHE-0015', 'Jawahar Navodaya Vidyalaya, Kancheepuram','Kancheepuram',     'Tamil Nadu',  '631501'),
  ('KOL-0008', 'St. Xavier''s Collegiate School',         'Kolkata',          'West Bengal', '700016'),
  ('BLR-0033', 'Delhi Public School, Bangalore East',     'Bengaluru Urban',  'Karnataka',   '560049')
ON CONFLICT (center_code) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────
-- 2. STAFF USERS (Admin, Coordinator, HE, 2 AEs, 2 Invigilators)
-- ─────────────────────────────────────────────────────────────────────────
INSERT INTO users (name, email, password_hash, role, staff_code, is_active) VALUES

  -- ADMIN
  ('Rajesh Kumar Sharma',
   'admin@cbse.nic.in',
   '$2b$12$rPvw6yiIVCaNFFTDF3fkguwsSksY1CJV.toobqLB2xKThZeuljf5K',
   'ADMIN', 'CBSE-ADM-001', TRUE),

  -- COORDINATOR
  ('Priya Nair',
   'coordinator@cbse.nic.in',
   '$2b$12$sW6VzEg5wfMpL9C/UVxuseFOMLRGMD8Jiaksn22cZET4ISnRemyM.',
   'COORDINATOR', 'CBSE-CRD-001', TRUE),

  -- HEAD EXAMINER
  ('Dr. Anil Mehta',
   'he@cbse.nic.in',
   '$2b$12$Z0JQ6wAB2nfkxXmOGcNSze7W/Sq76vJ8O0C5B5rAs.xQ6GlThZDLm',
   'HEAD_EXAMINER', 'CBSE-HE-001', TRUE),

  -- ASSISTANT EXAMINERS
  ('Sunita Reddy',
   'ae1@cbse.nic.in',
   '$2b$12$2dsz9muythUfEfOztOmYwudPYBNKJI9OgcqP3SLFxBLs7yEgyMsma',
   'ASSISTANT_EXAMINER', 'CBSE-AE-001', TRUE),

  ('Mohammed Farhan',
   'ae2@cbse.nic.in',
   '$2b$12$2dsz9muythUfEfOztOmYwudPYBNKJI9OgcqP3SLFxBLs7yEgyMsma',
   'ASSISTANT_EXAMINER', 'CBSE-AE-002', TRUE),

  -- INVIGILATORS
  ('Deepa Krishnamurthy',
   'invig1@cbse.nic.in',
   '$2b$12$AdqPmHzHVTOCMPQ9mllcCuz4mEz24G0Wj0c9StpFDwYgI2QhebQym',
   'INVIGILATOR', 'CBSE-INV-001', TRUE),

  ('Arjun Singh Rawat',
   'invig2@cbse.nic.in',
   '$2b$12$AdqPmHzHVTOCMPQ9mllcCuz4mEz24G0Wj0c9StpFDwYgI2QhebQym',
   'INVIGILATOR', 'CBSE-INV-002', TRUE)

ON CONFLICT (email) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────
-- 3. CLASS 12 STUDENTS (10 students)
-- ─────────────────────────────────────────────────────────────────────────
INSERT INTO users (name, email, password_hash, role, class_level, roll_number, is_active) VALUES
  ('Aarav Patel',         'aarav.patel@student.cbse.in',       '$2b$12$TuqUF6m/9nczYnfIwpbeded8yFjTlWhm8HPP65vIhRGQHi3oqO1TW', 'STUDENT', 12, 'DEL12-2601', TRUE),
  ('Ishaan Verma',        'ishaan.verma@student.cbse.in',      '$2b$12$h28oo/zhmub9NsYl9Y9gNOs1e1WjkIFEAzJP5UnSG4m/YF.tpo0ku', 'STUDENT', 12, 'DEL12-2602', TRUE),
  ('Ananya Krishnan',     'ananya.krishnan@student.cbse.in',   '$2b$12$B0rg6zCaVwylO768XI7.5uXsB.CY6zW7Fo6rnZ4VZhlrwW.GYF9Fu', 'STUDENT', 12, 'DEL12-2603', TRUE),
  ('Rohan Gupta',         'rohan.gupta@student.cbse.in',       '$2b$12$YB8QuGWm2zW9HF91vRyInOA1DR.EgWWtPXlDSRqfKqgjpdJA.uOxi', 'STUDENT', 12, 'DEL12-2604', TRUE),
  ('Kavya Nambiar',       'kavya.nambiar@student.cbse.in',     '$2b$12$t9BCanUtsw29xzK7HpjBwOCqOovEq1Ko3G.wTAfus000KZb6KHTDe', 'STUDENT', 12, 'MUM12-2605', TRUE),
  ('Aryan Malhotra',      'aryan.malhotra@student.cbse.in',    '$2b$12$UB8yp75nCDH4PPq2o4qHAOk5tm3fBP8rK0dcBxyQag4kF7Yj0GMpG', 'STUDENT', 12, 'MUM12-2606', TRUE),
  ('Meera Subramaniam',   'meera.subramaniam@student.cbse.in', '$2b$12$cdR22O6LSxnETB6jW/q1.eSnaAuRqzHbcf52JVHdUvaM89eIow6GO', 'STUDENT', 12, 'CHE12-2607', TRUE),
  ('Vivaan Kapoor',       'vivaan.kapoor@student.cbse.in',     '$2b$12$mbyyj8GxpBEYSP24K3YPoeFK632F2oSuhV9rld62q9NGuChKCQ2v2', 'STUDENT', 12, 'CHE12-2608', TRUE),
  ('Saanvi Joshi',        'saanvi.joshi@student.cbse.in',      '$2b$12$NVb1MseWW7PDRYR5SSNok.8WVAumyj0x787nCDQ035iQNob9W92Uq', 'STUDENT', 12, 'KOL12-2609', TRUE),
  ('Dhruv Chatterjee',    'dhruv.chatterjee@student.cbse.in',  '$2b$12$jSgOPm9r0yN5G2pJdwPvg.w4boy72EVU8jMFLWu7FL1DLiAGv87da', 'STUDENT', 12, 'KOL12-2610', TRUE)
ON CONFLICT (email) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────
-- 4. CLASS 10 STUDENTS (10 students)
-- ─────────────────────────────────────────────────────────────────────────
INSERT INTO users (name, email, password_hash, role, class_level, roll_number, is_active) VALUES
  ('Aditya Sharma',       'aditya.sharma@student.cbse.in',     '$2b$12$oVef4aeUUXh.xhHHNuBLRekVl3WYl1eYqjRkwf3cBclNsYO.wWj5G', 'STUDENT', 10, 'DEL10-2601', TRUE),
  ('Riya Bhatt',          'riya.bhatt@student.cbse.in',        '$2b$12$8ryuQ99U./H9leFzieSZT.ozWlJfq1w0KDoVIEg.uc/4BHIWRh6e6', 'STUDENT', 10, 'DEL10-2602', TRUE),
  ('Siddharth Iyer',      'siddharth.iyer@student.cbse.in',    '$2b$12$PvVKJLxnH.UehYKLfcuUT.qZLVi9vWFJr/6WbS5FV9BmGEGTNC/F6', 'STUDENT', 10, 'DEL10-2603', TRUE),
  ('Pooja Desai',         'pooja.desai@student.cbse.in',       '$2b$12$ELIvqjGjNLlRQWRvjroAiOAdMuOWQXF97d3Bk1sFXqaCXd6JH6lzy', 'STUDENT', 10, 'MUM10-2604', TRUE),
  ('Nikhil Menon',        'nikhil.menon@student.cbse.in',      '$2b$12$I/I9p6.QQxLLZfY0jhw/2eq1X0yhOohNNZzT8yGxtlPmLNdTZFvTm', 'STUDENT', 10, 'MUM10-2605', TRUE),
  ('Tanvi Rao',           'tanvi.rao@student.cbse.in',         '$2b$12$AgBopopbXiEQgKGEQ5O6V.TLvtIacmhrvCq9Wm677tNnewuwVAkuy', 'STUDENT', 10, 'CHE10-2606', TRUE),
  ('Karan Singhania',     'karan.singhania@student.cbse.in',   '$2b$12$4NBIm05aRyQFEY6knQoqduGd9zRGLvnn7HLAsZXwayP5y/XEtR9.K', 'STUDENT', 10, 'CHE10-2607', TRUE),
  ('Anushka Pillai',      'anushka.pillai@student.cbse.in',    '$2b$12$amLSGIc8kNaGHC7/7tSsLuOlc6R38UBjSdeN22yaRoI3kOBThSIFW', 'STUDENT', 10, 'KOL10-2608', TRUE),
  ('Yash Agarwal',        'yash.agarwal@student.cbse.in',      '$2b$12$VHBqZ.xvkfkuLmV5VU10FeXSPem.a97W3bDeqWnLRWccaliBnqJrC', 'STUDENT', 10, 'KOL10-2609', TRUE),
  ('Diya Bose',           'diya.bose@student.cbse.in',         '$2b$12$Q1z1qL7Mc4tDLu5leVFOGuEtl3y8lMfKixVIoLId29dDLaJvl/QRe', 'STUDENT', 10, 'BLR10-2610', TRUE)
ON CONFLICT (email) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────
-- 5. EXAMS — Class 12 (OSM enabled) + Class 10 (Physical)
--    CBSE 2026 official timetable subjects
-- ─────────────────────────────────────────────────────────────────────────
INSERT INTO exams (subject, subject_code, exam_date, start_time, duration_minutes,
                   center_id, class_level, total_marks, practical_marks, osm_enabled) VALUES

  -- CLASS 12 (OSM = TRUE, theory 80 + practical 20)
  ('English Core',        '301', '2026-02-15', '10:30:00', 180, 1, 12, 80, 20, TRUE),
  ('Physics',             '042', '2026-02-20', '10:30:00', 180, 1, 12, 70, 30, TRUE),
  ('Chemistry',           '043', '2026-02-25', '10:30:00', 180, 1, 12, 70, 30, TRUE),
  ('Mathematics',         '041', '2026-03-01', '10:30:00', 180, 2, 12, 80, 20, TRUE),
  ('Computer Science',    '083', '2026-03-05', '10:30:00', 180, 2, 12, 70, 30, TRUE),
  ('Biology',             '044', '2026-03-10', '10:30:00', 180, 3, 12, 70, 30, TRUE),
  ('History',             '027', '2026-03-15', '10:30:00', 180, 3, 12, 80, 20, TRUE),
  ('Accountancy',         '055', '2026-03-20', '10:30:00', 180, 4, 12, 80, 20, TRUE),

  -- CLASS 10 (OSM = FALSE, physical marking)
  ('English Language & Literature', '184', '2026-02-17', '10:30:00', 180, 1, 10, 80, 20, FALSE),
  ('Science',             '086', '2026-02-22', '10:30:00', 180, 1, 10, 80, 20, FALSE),
  ('Mathematics Standard','041', '2026-02-27', '10:30:00', 180, 2, 10, 80, 20, FALSE),
  ('Social Science',      '087', '2026-03-03', '10:30:00', 180, 2, 10, 80, 20, FALSE),
  ('Hindi Course-B',      '085', '2026-03-08', '10:30:00', 180, 3, 10, 80, 20, FALSE),
  ('Information Technology','402','2026-03-12', '10:30:00', 120, 3, 10, 50, 50, FALSE)

ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────
-- 6. ENROLLMENTS — Each Class 12 student in 4 exams, Class 10 in 4 exams
-- ─────────────────────────────────────────────────────────────────────────

-- Helper: get IDs by roll number and exam by subject+class
-- Class 12 enrollments (exam_ids 1–8, student roll DEL12-2601 to KOL12-2610)
INSERT INTO student_exam_enrollment (student_id, exam_id, seat_number, is_present)
SELECT u.id, e.exam_id,
       CONCAT(LEFT(u.roll_number,3), '-', LPAD(ROW_NUMBER() OVER (PARTITION BY e.exam_id ORDER BY u.roll_number)::TEXT, 2,'0')),
       TRUE
FROM users u
CROSS JOIN exams e
WHERE u.class_level = 12
  AND u.role = 'STUDENT'
  AND e.class_level = 12
  AND e.exam_id IN (1,2,3,4)  -- Enroll all Class 12 students in first 4 Class 12 exams
ON CONFLICT (student_id, exam_id) DO NOTHING;

-- Class 10 enrollments
INSERT INTO student_exam_enrollment (student_id, exam_id, seat_number, is_present)
SELECT u.id, e.exam_id,
       CONCAT(LEFT(u.roll_number,3), '-', LPAD(ROW_NUMBER() OVER (PARTITION BY e.exam_id ORDER BY u.roll_number)::TEXT, 2,'0')),
       TRUE
FROM users u
CROSS JOIN exams e
WHERE u.class_level = 10
  AND u.role = 'STUDENT'
  AND e.class_level = 10
  AND e.exam_id IN (9,10,11,12)  -- Enroll all Class 10 students in first 4 Class 10 exams
ON CONFLICT (student_id, exam_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────
-- 7. SCRIPTS — Class 12 with OSM masked IDs + Class 10 with PHY- prefix
--    Covering exam_ids 1 (English Core Cl12) and 9 (English Cl10)
--    with realistic marks across all pipeline stages
-- ─────────────────────────────────────────────────────────────────────────

-- Get staff IDs for use in script population
DO $$
DECLARE
  v_ae1_id    INT;
  v_ae2_id    INT;
  v_he_id     INT;
  v_coord_id  INT;
BEGIN
  SELECT id INTO v_ae1_id   FROM users WHERE staff_code = 'CBSE-AE-001';
  SELECT id INTO v_ae2_id   FROM users WHERE staff_code = 'CBSE-AE-002';
  SELECT id INTO v_he_id    FROM users WHERE staff_code = 'CBSE-HE-001';
  SELECT id INTO v_coord_id FROM users WHERE staff_code = 'CBSE-CRD-001';

  -- ── CLASS 12: English Core (exam_id=1), OSM masked IDs ──────────────

  INSERT INTO scripts (student_id, masked_id, exam_id, subject, class_level, status,
    ae_marks, he_marks, final_marks, he_review_needed,
    ae_staff_id, he_staff_id, coordinator_id,
    ae_marked_at, he_reviewed_at, finalized_at)
  SELECT
    u.id,
    CASE u.roll_number
      WHEN 'DEL12-2601' THEN 'BX-2026-A3F9K2'
      WHEN 'DEL12-2602' THEN 'BX-2026-B7M4P1'
      WHEN 'DEL12-2603' THEN 'BX-2026-C2N8Q5'
      WHEN 'DEL12-2604' THEN 'BX-2026-D5R1W9'
      WHEN 'MUM12-2605' THEN 'BX-2026-E9T6X3'
      WHEN 'MUM12-2606' THEN 'BX-2026-F4V2Y7'
      WHEN 'CHE12-2607' THEN 'BX-2026-G8Z3H1'
      WHEN 'CHE12-2608' THEN 'BX-2026-H1J7K4'
      WHEN 'KOL12-2609' THEN 'BX-2026-J6L9M2'
      WHEN 'KOL12-2610' THEN 'BX-2026-K3N5P8'
    END,
    1, 'English Core', 12,
    CASE u.roll_number
      WHEN 'DEL12-2601' THEN 'UPLOADED'
      WHEN 'DEL12-2602' THEN 'UPLOADED'
      WHEN 'DEL12-2603' THEN 'COORDINATOR_VERIFIED'
      WHEN 'DEL12-2604' THEN 'COORDINATOR_VERIFIED'
      WHEN 'MUM12-2605' THEN 'HE_REVIEWED'
      WHEN 'MUM12-2606' THEN 'AE_MARKED'
      WHEN 'CHE12-2607' THEN 'AE_MARKED'
      WHEN 'CHE12-2608' THEN 'PENDING'
      WHEN 'KOL12-2609' THEN 'PENDING'
      WHEN 'KOL12-2610' THEN 'PENDING'
    END,
    -- ae_marks
    CASE u.roll_number
      WHEN 'DEL12-2601' THEN 72  WHEN 'DEL12-2602' THEN 65
      WHEN 'DEL12-2603' THEN 58  WHEN 'DEL12-2604' THEN 78
      WHEN 'MUM12-2605' THEN 61  WHEN 'MUM12-2606' THEN 54
      WHEN 'CHE12-2607' THEN 70  ELSE 0
    END,
    -- he_marks (only where HE_REVIEWED)
    CASE u.roll_number
      WHEN 'DEL12-2601' THEN 73  WHEN 'DEL12-2602' THEN 65
      WHEN 'MUM12-2605' THEN 63  ELSE 0
    END,
    -- final_marks (where COORDINATOR_VERIFIED+)
    CASE u.roll_number
      WHEN 'DEL12-2601' THEN 73  WHEN 'DEL12-2602' THEN 65
      WHEN 'DEL12-2603' THEN 58  WHEN 'DEL12-2604' THEN 78
      ELSE 0
    END,
    -- he_review_needed
    u.roll_number IN ('DEL12-2601','DEL12-2602','MUM12-2605'),
    -- ae_staff_id
    CASE WHEN u.roll_number NOT IN ('CHE12-2608','KOL12-2609','KOL12-2610')
         THEN v_ae1_id ELSE NULL END,
    -- he_staff_id
    CASE WHEN u.roll_number IN ('DEL12-2601','DEL12-2602','MUM12-2605')
         THEN v_he_id ELSE NULL END,
    -- coordinator_id
    CASE WHEN u.roll_number IN ('DEL12-2601','DEL12-2602','DEL12-2603','DEL12-2604')
         THEN v_coord_id ELSE NULL END,
    -- ae_marked_at
    CASE WHEN u.roll_number NOT IN ('CHE12-2608','KOL12-2609','KOL12-2610')
         THEN NOW() - INTERVAL '3 days' ELSE NULL END,
    -- he_reviewed_at
    CASE WHEN u.roll_number IN ('DEL12-2601','DEL12-2602','MUM12-2605')
         THEN NOW() - INTERVAL '2 days' ELSE NULL END,
    -- finalized_at
    CASE WHEN u.roll_number IN ('DEL12-2601','DEL12-2602','DEL12-2603','DEL12-2604')
         THEN NOW() - INTERVAL '1 day' ELSE NULL END
  FROM users u
  WHERE u.role = 'STUDENT' AND u.class_level = 12;

  -- ── CLASS 10: English (exam_id=9), PHYSICAL PHY- prefix ─────────────

  INSERT INTO scripts (student_id, masked_id, exam_id, subject, class_level, status,
    ae_marks, he_marks, final_marks, he_review_needed,
    section_a_marks, section_b_marks, section_c_marks, section_d_marks,
    ae_staff_id, he_staff_id, coordinator_id,
    ae_marked_at, he_reviewed_at, finalized_at)
  SELECT
    u.id,
    CONCAT('PHY-', u.roll_number),
    9, 'English Language & Literature', 10,
    CASE u.roll_number
      WHEN 'DEL10-2601' THEN 'UPLOADED'
      WHEN 'DEL10-2602' THEN 'COORDINATOR_VERIFIED'
      WHEN 'DEL10-2603' THEN 'HE_REVIEWED'
      WHEN 'MUM10-2604' THEN 'AE_MARKED'
      WHEN 'MUM10-2605' THEN 'AE_MARKED'
      ELSE 'PENDING'
    END,
    -- ae_marks
    CASE u.roll_number
      WHEN 'DEL10-2601' THEN 68  WHEN 'DEL10-2602' THEN 74
      WHEN 'DEL10-2603' THEN 55  WHEN 'MUM10-2604' THEN 62
      WHEN 'MUM10-2605' THEN 79  ELSE 0
    END,
    -- he_marks
    CASE u.roll_number
      WHEN 'DEL10-2601' THEN 68  WHEN 'DEL10-2603' THEN 57
      ELSE 0
    END,
    -- final_marks
    CASE u.roll_number
      WHEN 'DEL10-2601' THEN 68  WHEN 'DEL10-2602' THEN 74
      ELSE 0
    END,
    -- he_review_needed
    u.roll_number IN ('DEL10-2601','DEL10-2603'),
    -- section marks (A+B+C+D = ae_marks for marked scripts)
    CASE u.roll_number
      WHEN 'DEL10-2601' THEN 20  WHEN 'DEL10-2602' THEN 22
      WHEN 'DEL10-2603' THEN 16  WHEN 'MUM10-2604' THEN 18
      WHEN 'MUM10-2605' THEN 24  ELSE 0
    END,
    CASE u.roll_number
      WHEN 'DEL10-2601' THEN 18  WHEN 'DEL10-2602' THEN 20
      WHEN 'DEL10-2603' THEN 14  WHEN 'MUM10-2604' THEN 16
      WHEN 'MUM10-2605' THEN 22  ELSE 0
    END,
    CASE u.roll_number
      WHEN 'DEL10-2601' THEN 18  WHEN 'DEL10-2602' THEN 20
      WHEN 'DEL10-2603' THEN 14  WHEN 'MUM10-2604' THEN 16
      WHEN 'MUM10-2605' THEN 22  ELSE 0
    END,
    CASE u.roll_number
      WHEN 'DEL10-2601' THEN 12  WHEN 'DEL10-2602' THEN 12
      WHEN 'DEL10-2603' THEN 11  WHEN 'MUM10-2604' THEN 12
      WHEN 'MUM10-2605' THEN 11  ELSE 0
    END,
    CASE WHEN u.roll_number NOT IN ('CHE10-2606','CHE10-2607','KOL10-2608','KOL10-2609','BLR10-2610')
         THEN v_ae2_id ELSE NULL END,
    CASE WHEN u.roll_number IN ('DEL10-2601','DEL10-2603')
         THEN v_he_id ELSE NULL END,
    CASE WHEN u.roll_number IN ('DEL10-2601','DEL10-2602')
         THEN v_coord_id ELSE NULL END,
    CASE WHEN u.roll_number NOT IN ('CHE10-2606','CHE10-2607','KOL10-2608','KOL10-2609','BLR10-2610')
         THEN NOW() - INTERVAL '4 days' ELSE NULL END,
    CASE WHEN u.roll_number IN ('DEL10-2601','DEL10-2603')
         THEN NOW() - INTERVAL '3 days' ELSE NULL END,
    CASE WHEN u.roll_number IN ('DEL10-2601','DEL10-2602')
         THEN NOW() - INTERVAL '2 days' ELSE NULL END
  FROM users u
  WHERE u.role = 'STUDENT' AND u.class_level = 10;

  -- ── CLASS 12: Physics (exam_id=2) — All PENDING (just enrolled) ────
  INSERT INTO scripts (student_id, masked_id, exam_id, subject, class_level, status)
  SELECT
    u.id,
    CONCAT('BX-2026-PH', LPAD(ROW_NUMBER() OVER (ORDER BY u.roll_number)::TEXT, 4, '0')),
    2, 'Physics', 12, 'PENDING'
  FROM users u
  WHERE u.role = 'STUDENT' AND u.class_level = 12;

  -- ── CLASS 10: Science (exam_id=10) — All PENDING ────────────────────
  INSERT INTO scripts (student_id, masked_id, exam_id, subject, class_level, status)
  SELECT
    u.id,
    CONCAT('PHY-SC-', u.roll_number),
    10, 'Science', 10, 'PENDING'
  FROM users u
  WHERE u.role = 'STUDENT' AND u.class_level = 10;

END $$;

-- ─────────────────────────────────────────────────────────────────────────
-- 8. AUDIT LOGS — Realistic historical activity
-- ─────────────────────────────────────────────────────────────────────────
INSERT INTO audit_logs (user_id, action, target_entity, target_id, detail, timestamp)
SELECT
  (SELECT id FROM users WHERE staff_code = 'CBSE-ADM-001'),
  'USER_REGISTERED', 'user', id,
  'Bulk registration: ' || name || ' (' || role || ')',
  NOW() - INTERVAL '10 days'
FROM users WHERE role != 'STUDENT' AND staff_code != 'CBSE-ADM-001';

INSERT INTO audit_logs (user_id, action, target_entity, target_id, detail, timestamp)
SELECT
  (SELECT id FROM users WHERE staff_code = 'CBSE-ADM-001'),
  'EXAM_CREATED', 'exam', exam_id,
  subject || ' (' || subject_code || ') Class ' || class_level || ' on ' || exam_date,
  NOW() - INTERVAL '7 days'
FROM exams;

INSERT INTO audit_logs (user_id, action, target_entity, target_id, detail, timestamp)
SELECT
  (SELECT id FROM users WHERE staff_code = 'CBSE-ADM-001'),
  'SCRIPT_GENERATED', 'script', script_id,
  'Script generated: masked_id=' || masked_id || ' for exam_id=' || exam_id,
  NOW() - INTERVAL '5 days'
FROM scripts;

INSERT INTO audit_logs (user_id, action, target_entity, target_id, detail, timestamp)
SELECT
  ae_staff_id, 'AE_MARKS_ENTERED', 'script', script_id,
  'MaskedID=' || masked_id || ' Marks=' || ae_marks,
  ae_marked_at
FROM scripts
WHERE ae_staff_id IS NOT NULL AND ae_marked_at IS NOT NULL;

INSERT INTO audit_logs (user_id, action, target_entity, target_id, detail, timestamp)
SELECT
  he_staff_id, 'HE_MARKS_ENTERED', 'script', script_id,
  'MaskedID=' || masked_id || ' HE=' || he_marks || ' AE was=' || ae_marks,
  he_reviewed_at
FROM scripts
WHERE he_staff_id IS NOT NULL AND he_reviewed_at IS NOT NULL;

INSERT INTO audit_logs (user_id, action, target_entity, target_id, detail, timestamp)
SELECT
  coordinator_id, 'COORDINATOR_FINALIZED', 'script', script_id,
  'MaskedID=' || masked_id || ' FinalMarks=' || final_marks,
  finalized_at
FROM scripts
WHERE coordinator_id IS NOT NULL AND finalized_at IS NOT NULL;

INSERT INTO audit_logs (user_id, action, target_entity, target_id, detail, timestamp)
SELECT
  coordinator_id, 'PORTAL_UPLOAD', 'script', script_id,
  'Uploaded to CBSE portal: ' || masked_id,
  finalized_at + INTERVAL '30 minutes'
FROM scripts
WHERE status = 'UPLOADED' AND coordinator_id IS NOT NULL;

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────
-- VERIFICATION QUERIES — Run these to confirm seed was successful
-- ─────────────────────────────────────────────────────────────────────────
SELECT '=== SEED SUMMARY ===' AS info;

SELECT 'Users by role' AS category, role, COUNT(*) AS total
FROM users GROUP BY role ORDER BY role;

SELECT 'Exams by class' AS category, class_level, COUNT(*) AS total,
       SUM(CASE WHEN osm_enabled THEN 1 ELSE 0 END) AS osm_count
FROM exams GROUP BY class_level;

SELECT 'Scripts by status' AS category, status, COUNT(*) AS total
FROM scripts GROUP BY status ORDER BY status;

SELECT 'Audit log entries' AS category, COUNT(*) AS total FROM audit_logs;

SELECT 'Enrollments' AS category, COUNT(*) AS total FROM student_exam_enrollment;
