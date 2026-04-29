package com.boardexam.service;

import com.boardexam.model.*;
import com.boardexam.repository.DataRepository;
import com.boardexam.util.ConsoleUI;

import java.sql.SQLException;
import java.util.List;

/**
 * StudentPortal — All student-facing operations.
 *
 * A student can:
 *  1. View their exam schedule (dates, center, time, seat number)
 *  2. View CBSE exam rules for their class level
 *  3. Check OSM scanning status (Class 12)
 *  4. View section-wise marking scheme (Class 10)
 *  5. Check result status (once finalized)
 */
public class StudentPortal {

    private final DataRepository repo;
    private final User student;

    public StudentPortal(DataRepository repo, User student) {
        this.repo    = repo;
        this.student = student;
    }

    public void run() {
        while (true) {
            ConsoleUI.printSectionHeader("Student Portal — " + student.getName() +
                " | " + student.getClassLevel().getDescription());
            ConsoleUI.println("  1. View My Exam Schedule");
            ConsoleUI.println("  2. CBSE Exam Rules & Guidelines");
            ConsoleUI.println("  3. Marking Scheme Info");
            if (student.isClass12()) {
                ConsoleUI.println("  4. OSM Scanning Status (Class 12)");
            } else {
                ConsoleUI.println("  4. Section-wise Marking Rules (Class 10)");
            }
            ConsoleUI.println("  5. Check My Result Status");
            ConsoleUI.println("  0. Logout");

            int choice = ConsoleUI.promptInt("Choice", 0, 5);
            switch (choice) {
                case 1 -> viewExamSchedule();
                case 2 -> showExamRules();
                case 3 -> showMarkingScheme();
                case 4 -> { if (student.isClass12()) showOsmStatus(); else showClass10Rules(); }
                case 5 -> checkResultStatus();
                case 0 -> { return; }
            }
            ConsoleUI.pressEnterToContinue();
        }
    }

    private void viewExamSchedule() {
        ConsoleUI.printSectionHeader("My Exam Schedule");
        try {
            List<Exam> exams = repo.getExamsForStudent(student.getId());
            if (exams.isEmpty()) {
                ConsoleUI.warn("No exams found. Contact your school for enrollment.");
                return;
            }

            String[][] rows = new String[exams.size()][6];
            for (int i = 0; i < exams.size(); i++) {
                Exam e = exams.get(i);
                rows[i] = new String[]{
                    e.getSubjectCode(),
                    e.getSubject(),
                    e.getExamDate().toString(),
                    e.getStartTime() + " – " + e.getEndTime(),
                    e.getCenterName() != null ? e.getCenterName() : "TBA",
                    e.getGradingType()
                };
            }
            ConsoleUI.printTable(
                new String[]{"Code", "Subject", "Date", "Time", "Center", "Mode"},
                rows);

            ConsoleUI.println();
            ConsoleUI.info("Reporting time: 30 minutes before exam start.");
            ConsoleUI.info("Carry: Admit Card + Photo ID (Aadhaar / School ID).");

        } catch (SQLException e) {
            ConsoleUI.error("Error fetching schedule: " + e.getMessage());
        }
    }

    private void showExamRules() {
        ConsoleUI.printSectionHeader("CBSE 2026 Examination Rules");

        ConsoleUI.bold("  GENERAL RULES (All Classes):");
        ConsoleUI.println("  ┌──────────────────────────────────────────────────────────────┐");
        ConsoleUI.println("  │ 1. Arrive 30 minutes before exam. Entry closed at start time. │");
        ConsoleUI.println("  │ 2. Carry ORIGINAL Admit Card + Govt-issued photo ID.          │");
        ConsoleUI.println("  │ 3. Electronic devices (phones, smartwatches) strictly banned. │");
        ConsoleUI.println("  │ 4. Use only blue/black ballpoint pen for answer sheets.       │");
        ConsoleUI.println("  │ 5. Do not write your name on the answer booklet — use roll no.│");
        ConsoleUI.println("  │ 6. Attempt all questions unless stated otherwise.              │");
        ConsoleUI.println("  └──────────────────────────────────────────────────────────────┘");

        if (student.isClass12()) {
            ConsoleUI.println();
            ConsoleUI.bold("  CLASS 12 — SPECIFIC RULES:");
            ConsoleUI.println("  ┌──────────────────────────────────────────────────────────────┐");
            ConsoleUI.println("  │ OSM: Your answer sheet will be scanned and marked digitally.  │");
            ConsoleUI.println("  │ Write WITHIN the printed margins — marks outside are skipped. │");
            ConsoleUI.println("  │ Do not use pencil in theory papers (scanner may miss it).     │");
            ConsoleUI.println("  │ Diagrams: Label clearly; examiner views on screen.            │");
            ConsoleUI.println("  │ Page tear / mutilation = script cancelled.                   │");
            ConsoleUI.println("  └──────────────────────────────────────────────────────────────┘");
        } else {
            ConsoleUI.println();
            ConsoleUI.bold("  CLASS 10 — SPECIFIC RULES:");
            ConsoleUI.println("  ┌──────────────────────────────────────────────────────────────┐");
            ConsoleUI.println("  │ Physical scripts — handwriting legibility is evaluated.       │");
            ConsoleUI.println("  │ Section A (MCQ): Darken the correct circle fully.             │");
            ConsoleUI.println("  │ Section B/C: Answer in sequence; leave 1 line between answers.│");
            ConsoleUI.println("  │ Maps (Social Science): Use pencil for maps only; pen for rest.│");
            ConsoleUI.println("  │ Rough work: Only in designated rough work area.               │");
            ConsoleUI.println("  └──────────────────────────────────────────────────────────────┘");
        }
    }

    private void showMarkingScheme() {
        ConsoleUI.printSectionHeader("Marking Scheme — Class " + student.getClassLevel().getLevel());
        if (student.isClass12()) {
            ConsoleUI.bold("  Theory: 80 marks  |  Practical/IA: 20 marks");
            ConsoleUI.println();
            ConsoleUI.println("  Typical paper structure:");
            ConsoleUI.printTable(
                new String[]{"Section", "Type", "Questions", "Marks Each", "Total"},
                new String[][]{
                    {"A", "MCQ / Assertion-Reason", "16",  "1",  "16"},
                    {"B", "Very Short Answer",       "5",   "2",  "10"},
                    {"C", "Short Answer",            "7",   "3",  "21"},
                    {"D", "Long Answer / Case Study","3",   "5",  "15"},
                    {"E", "Long Answer",             "3",   "6",  "18"},
                    {"",  "TOTAL",                   "",    "",   "80"},
                });
        } else {
            ConsoleUI.bold("  Theory: 80 marks  |  Internal Assessment: 20 marks");
            ConsoleUI.println();
            ConsoleUI.println("  Typical paper structure:");
            ConsoleUI.printTable(
                new String[]{"Section", "Type", "Questions", "Marks Each", "Total"},
                new String[][]{
                    {"A", "MCQ / Fill in blanks",   "20",  "1",  "20"},
                    {"B", "Very Short Answer",       "6",   "2",  "12"},
                    {"C", "Short Answer-I",          "7",   "3",  "21"},
                    {"D", "Long Answer",             "3",   "5",  "15"},
                    {"E", "Source / Map / Case",     "3",   "4",  "12"},
                    {"",  "TOTAL",                   "",    "",   "80"},
                });
        }
        ConsoleUI.println();
        ConsoleUI.info("Negative marking: NOT applicable in CBSE 2026 board exams.");
        ConsoleUI.info("Internal Choice: Available in Sections B, C, D, E.");
    }

    private void showOsmStatus() {
        ConsoleUI.printSectionHeader("OSM (On-Screen Marking) Scanning Status");
        ConsoleUI.info("Your answer scripts are scanned at the exam center after collection.");
        ConsoleUI.println();
        ConsoleUI.bold("  OSM Pipeline:");
        ConsoleUI.println("  Script collected → Barcode tagged → Scanned at NIC facility");
        ConsoleUI.println("  → Anonymized (your name removed) → Uploaded to CBSE cloud");
        ConsoleUI.println("  → Assigned to evaluator → Marks entered → HE spot-check");
        ConsoleUI.println("  → Regional Coordinator verifies → Results declared");
        ConsoleUI.println();
        ConsoleUI.warn("  Note: OSM scanning status is available only after exam date.");
        ConsoleUI.info("  Check results at: cbseresults.nic.in after declaration date.");

        try {
            List<Exam> exams = repo.getExamsForStudent(student.getId());
            for (Exam e : exams) {
                if (e.isOsmEnabled()) {
                    ConsoleUI.println();
                    ConsoleUI.printKeyValue(e.getSubject(), e.getExamDate().isBefore(java.time.LocalDate.now())
                        ? "Scan pending / In evaluation" : "Exam not yet held");
                }
            }
        } catch (SQLException e) {
            ConsoleUI.error("Error: " + e.getMessage());
        }
    }

    private void showClass10Rules() {
        ConsoleUI.printSectionHeader("Class 10 — Section-Wise Marking Compliance");
        ConsoleUI.bold("  CBSE 2026 Physical Compliance Rules:");
        ConsoleUI.println();
        ConsoleUI.println("  ► Section A — Objective (MCQ / Fill-in / T-F):");
        ConsoleUI.println("    • Mark ONLY in the answer box. Overwriting = 0 marks.");
        ConsoleUI.println("    • Tick (✓) or cross (✗) in MCQ circles. No shading required.");
        ConsoleUI.println();
        ConsoleUI.println("  ► Section B — Very Short Answer (2 marks):");
        ConsoleUI.println("    • Answers must not exceed 30 words.");
        ConsoleUI.println("    • Each answer on a new line.");
        ConsoleUI.println();
        ConsoleUI.println("  ► Section C — Short Answer (3 marks):");
        ConsoleUI.println("    • Answers must not exceed 80 words.");
        ConsoleUI.println("    • Bullet points acceptable.");
        ConsoleUI.println();
        ConsoleUI.println("  ► Section D / E — Long Answer / Case Study (4-5 marks):");
        ConsoleUI.println("    • Answer in paragraph form.");
        ConsoleUI.println("    • Map work: Use pencil. Label countries/rivers exactly as asked.");
        ConsoleUI.println("    • Sources (History): Quote the source before your analysis.");
        ConsoleUI.println();
        ConsoleUI.info("Handwriting Policy: Examiners can deduct up to 2 marks for illegibility.");
    }

    private void checkResultStatus() {
        ConsoleUI.printSectionHeader("Result Status — Roll: " + student.getRollNumber());
        ConsoleUI.info("Results are declared on cbseresults.nic.in after the evaluation period.");
        ConsoleUI.println();
        ConsoleUI.printKeyValue("Roll Number",   student.getRollNumber());
        ConsoleUI.printKeyValue("Class",         String.valueOf(student.getClassLevel().getLevel()));
        ConsoleUI.printKeyValue("Evaluation Mode", student.getClassLevel().getGradingType());
        ConsoleUI.println();
        ConsoleUI.warn("Result status is not yet available in the local EEMS portal.");
        ConsoleUI.info("Contact your Regional Office for declared result queries.");
    }
}
