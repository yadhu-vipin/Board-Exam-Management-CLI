package com.boardexam.service;

import com.boardexam.model.*;
import com.boardexam.repository.DataRepository;
import com.boardexam.util.ConsoleUI;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * StaffPortal — Role-aware staff operations.
 *
 * Menu adapts dynamically based on the logged-in user's role:
 *   INVIGILATOR       → Duty management, seating, attendance marking
 *   ASSISTANT_EXAMINER→ OSM marking queue, section-wise entry
 *   HEAD_EXAMINER     → HE review queue, variance flagging
 *   COORDINATOR       → Finalization, upload, full stats
 *   ADMIN             → All of the above + exam creation + audit logs
 */
public class StaffPortal {

    private final DataRepository repo;
    private final EvaluationEngine evalEngine;
    private final User staff;

    public StaffPortal(DataRepository repo, User staff) {
        this.repo       = repo;
        this.staff      = staff;
        this.evalEngine = new EvaluationEngine(repo, staff);
    }

    public void run() {
        while (true) {
            ConsoleUI.printSectionHeader("Staff Portal — " + staff.getName() +
                " [" + staff.getRole() + "] | Code: " + staff.getStaffCode());

            printRoleMenu();

            int maxChoice = getMaxMenuChoice();
            int choice = ConsoleUI.promptInt("Choice", 0, maxChoice);

            if (choice == 0) return;
            handleChoice(choice);
            ConsoleUI.pressEnterToContinue();
        }
    }

    private void printRoleMenu() {
        ConsoleUI.println();
        switch (staff.getRole()) {
            case INVIGILATOR -> {
                ConsoleUI.println("  1. View My Invigilation Duties");
                ConsoleUI.println("  2. Mark Student Attendance");
                ConsoleUI.println("  3. View Seating Plan");
                ConsoleUI.println("  0. Logout");
            }
            case ASSISTANT_EXAMINER -> {
                ConsoleUI.println("  1. Load OSM Marking Queue");
                ConsoleUI.println("  2. View Evaluation Stats");
                ConsoleUI.println("  0. Logout");
            }
            case HEAD_EXAMINER -> {
                ConsoleUI.println("  1. Trigger 10% Random HE Flagging");
                ConsoleUI.println("  2. Start HE Review");
                ConsoleUI.println("  3. View Evaluation Stats");
                ConsoleUI.println("  0. Logout");
            }
            case COORDINATOR -> {
                ConsoleUI.println("  1. Generate Scripts for Exam");
                ConsoleUI.println("  2. Trigger 10% HE Flagging");
                ConsoleUI.println("  3. Finalize Scripts");
                ConsoleUI.println("  4. Upload to CBSE Portal");
                ConsoleUI.println("  5. View Evaluation Stats");
                ConsoleUI.println("  6. Enroll Student in Exam");
                ConsoleUI.println("  0. Logout");
            }
            case ADMIN -> {
                ConsoleUI.println("  1.  Create New Exam");
                ConsoleUI.println("  2.  Generate Scripts for Exam");
                ConsoleUI.println("  3.  Trigger 10% HE Flagging");
                ConsoleUI.println("  4.  AE Marking Queue");
                ConsoleUI.println("  5.  HE Review Queue");
                ConsoleUI.println("  6.  Finalize Scripts");
                ConsoleUI.println("  7.  Upload to CBSE Portal");
                ConsoleUI.println("  8.  View Evaluation Stats");
                ConsoleUI.println("  9.  Enroll Student in Exam");
                ConsoleUI.println("  10. View Audit Logs");
                ConsoleUI.println("  11. List All Exams");
                ConsoleUI.println("  0.  Logout");
            }
            default -> ConsoleUI.error("Unknown role.");
        }
        ConsoleUI.println();
    }

    private int getMaxMenuChoice() {
        return switch (staff.getRole()) {
            case INVIGILATOR        -> 3;
            case ASSISTANT_EXAMINER -> 2;
            case HEAD_EXAMINER      -> 3;
            case COORDINATOR        -> 6;
            case ADMIN              -> 11;
            default                 -> 0;
        };
    }

    private void handleChoice(int choice) {
        switch (staff.getRole()) {
            case INVIGILATOR        -> handleInvigilator(choice);
            case ASSISTANT_EXAMINER -> handleAE(choice);
            case HEAD_EXAMINER      -> handleHE(choice);
            case COORDINATOR        -> handleCoordinator(choice);
            case ADMIN              -> handleAdmin(choice);
        }
    }

    // ────────────────────────── INVIGILATOR ──────────────────────────────────

    private void handleInvigilator(int choice) {
        switch (choice) {
            case 1 -> showInvigilationDuties();
            case 2 -> markAttendance();
            case 3 -> showSeatingPlan();
        }
    }

    private void showInvigilationDuties() {
        ConsoleUI.printSectionHeader("My Invigilation Duties");
        ConsoleUI.info("Duties are assigned by the Center Superintendent.");
        ConsoleUI.println();
        ConsoleUI.printKeyValue("Staff Code",  staff.getStaffCode());
        ConsoleUI.printKeyValue("Role",        "Invigilator");
        ConsoleUI.println();
        ConsoleUI.bold("  CBSE 2026 Invigilator Duties:");
        ConsoleUI.println("  ► Collect answer booklets from Centre Superintendent.");
        ConsoleUI.println("  ► Distribute question papers exactly at exam start time.");
        ConsoleUI.println("  ► Verify student identity: Admit Card + Photo ID.");
        ConsoleUI.println("  ► Mark attendance on seating chart every 30 minutes.");
        ConsoleUI.println("  ► Collect all answer booklets before allowing exit.");
        ConsoleUI.println("  ► Seal booklets in CBSE-issued cover; hand to CS.");
        ConsoleUI.println("  ► No mobile phone inside exam hall.");
        ConsoleUI.println();
        ConsoleUI.warn("Any unfair means observed → Immediate report to Centre Superintendent.");
    }

    private void markAttendance() {
        ConsoleUI.printSectionHeader("Mark Student Attendance");
        ConsoleUI.info("This marks a student as PRESENT in the enrollment record.");
        int examId   = ConsoleUI.promptInt("Exam ID");
        String roll  = ConsoleUI.prompt("Student Roll Number");
        boolean present = ConsoleUI.promptYesNo("Mark as PRESENT?");
        ConsoleUI.info("Attendance marking requires DB enrollment record — see Coordinator.");
        ConsoleUI.info("[Simulated] Roll " + roll + " marked " + (present ? "PRESENT" : "ABSENT") +
            " for Exam #" + examId);
        try {
            repo.writeAuditLog(staff.getId(), "ATTENDANCE_MARKED", "enrollment", examId,
                "Roll=" + roll + " status=" + (present ? "PRESENT" : "ABSENT"));
        } catch (SQLException e) { ConsoleUI.error("Audit error: " + e.getMessage()); }
    }

    private void showSeatingPlan() {
        ConsoleUI.printSectionHeader("Seating Plan");
        int examId = ConsoleUI.promptInt("Exam ID");
        ConsoleUI.info("[Simulated] Seating plan for Exam #" + examId + ":");
        ConsoleUI.println("  Seat 001 — [Assigned Student]");
        ConsoleUI.println("  Seat 002 — [Assigned Student]");
        ConsoleUI.info("Full seating plan is printed and provided at center.");
    }

    // ────────────────────────── ASSISTANT EXAMINER ───────────────────────────

    private void handleAE(int choice) {
        switch (choice) {
            case 1 -> {
                int examId = ConsoleUI.promptInt("Enter Exam ID to mark");
                evalEngine.aeMarkingWorkflow(examId);
            }
            case 2 -> {
                int examId = ConsoleUI.promptInt("Enter Exam ID");
                evalEngine.showEvaluationStats(examId);
            }
        }
    }

    // ────────────────────────── HEAD EXAMINER ────────────────────────────────

    private void handleHE(int choice) {
        int examId;
        switch (choice) {
            case 1 -> {
                examId = ConsoleUI.promptInt("Enter Exam ID");
                evalEngine.triggerHEFlagging(examId);
            }
            case 2 -> {
                examId = ConsoleUI.promptInt("Enter Exam ID");
                evalEngine.heReviewWorkflow(examId);
            }
            case 3 -> {
                examId = ConsoleUI.promptInt("Enter Exam ID");
                evalEngine.showEvaluationStats(examId);
            }
        }
    }

    // ────────────────────────── COORDINATOR ──────────────────────────────────

    private void handleCoordinator(int choice) {
        int examId;
        switch (choice) {
            case 1 -> {
                examId = ConsoleUI.promptInt("Enter Exam ID to generate scripts for");
                evalEngine.generateScriptsForExam(examId);
            }
            case 2 -> {
                examId = ConsoleUI.promptInt("Enter Exam ID");
                evalEngine.triggerHEFlagging(examId);
            }
            case 3 -> {
                examId = ConsoleUI.promptInt("Enter Exam ID");
                evalEngine.coordinatorFinalizeWorkflow(examId);
            }
            case 4 -> {
                examId = ConsoleUI.promptInt("Enter Exam ID");
                evalEngine.simulatePortalUpload(examId);
            }
            case 5 -> {
                examId = ConsoleUI.promptInt("Enter Exam ID");
                evalEngine.showEvaluationStats(examId);
            }
            case 6 -> enrollStudentInExam();
        }
    }

    // ────────────────────────── ADMIN ─────────────────────────────────────────

    private void handleAdmin(int choice) {
        switch (choice) {
            case 1  -> createExam();
            case 2  -> { int e = ConsoleUI.promptInt("Exam ID"); evalEngine.generateScriptsForExam(e); }
            case 3  -> { int e = ConsoleUI.promptInt("Exam ID"); evalEngine.triggerHEFlagging(e); }
            case 4  -> { int e = ConsoleUI.promptInt("Exam ID"); evalEngine.aeMarkingWorkflow(e); }
            case 5  -> { int e = ConsoleUI.promptInt("Exam ID"); evalEngine.heReviewWorkflow(e); }
            case 6  -> { int e = ConsoleUI.promptInt("Exam ID"); evalEngine.coordinatorFinalizeWorkflow(e); }
            case 7  -> { int e = ConsoleUI.promptInt("Exam ID"); evalEngine.simulatePortalUpload(e); }
            case 8  -> { int e = ConsoleUI.promptInt("Exam ID"); evalEngine.showEvaluationStats(e); }
            case 9  -> enrollStudentInExam();
            case 10 -> viewAuditLogs();
            case 11 -> listAllExams();
        }
    }

    // ────────────────────────── SHARED ADMIN/COORDINATOR OPERATIONS ───────────

    private void createExam() {
        ConsoleUI.printSectionHeader("Create New Exam");

        String subject    = ConsoleUI.prompt("Subject Name (e.g. Physics)");
        String subjCode   = ConsoleUI.prompt("Subject Code (e.g. 042)");
        String dateStr    = ConsoleUI.prompt("Exam Date (YYYY-MM-DD)");
        String timeStr    = ConsoleUI.prompt("Start Time (HH:MM, e.g. 10:30)");
        int duration      = ConsoleUI.promptInt("Duration (minutes)", 60, 210);
        int classLvl      = ConsoleUI.promptInt("Class Level (10 or 12)", 10, 12);
        if (classLvl != 10 && classLvl != 12) { ConsoleUI.error("Must be 10 or 12."); return; }
        int totalMarks    = ConsoleUI.promptInt("Total Theory Marks", 40, 100);
        int practicalMarks= ConsoleUI.promptInt("Practical/IA Marks", 0, 30);
        boolean osmEnabled= classLvl == 12 && ConsoleUI.promptYesNo("Enable OSM for this paper?");

        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalTime time = LocalTime.parse(timeStr + ":00");

            Exam exam = new Exam();
            exam.setSubject(subject); exam.setSubjectCode(subjCode);
            exam.setExamDate(date); exam.setStartTime(time);
            exam.setDurationMinutes(duration);
            exam.setClassLevel(ClassLevel.fromInt(classLvl));
            exam.setTotalMarks(totalMarks); exam.setPracticalMarks(practicalMarks);
            exam.setOsmEnabled(osmEnabled);

            int newId = repo.createExam(exam);
            repo.writeAuditLog(staff.getId(), "EXAM_CREATED", "exam", newId,
                subject + " " + subjCode + " Class " + classLvl);
            ConsoleUI.success("Exam created! ID: " + newId);

        } catch (Exception e) {
            ConsoleUI.error("Error: " + e.getMessage());
        }
    }

    private void enrollStudentInExam() {
        ConsoleUI.printSectionHeader("Enroll Student in Exam");
        String roll    = ConsoleUI.prompt("Student Roll Number");
        int examId     = ConsoleUI.promptInt("Exam ID");
        String seat    = ConsoleUI.prompt("Seat Number (e.g. 015)");

        try {
            var student = repo.findUserByRollNumber(roll)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + roll));
            repo.enrollStudent(student.getId(), examId, seat);
            repo.writeAuditLog(staff.getId(), "STUDENT_ENROLLED", "enrollment", examId,
                "Roll=" + roll + " Seat=" + seat);
            ConsoleUI.success("Student " + roll + " enrolled in Exam #" + examId + " at seat " + seat);

        } catch (Exception e) {
            ConsoleUI.error("Enrollment error: " + e.getMessage());
        }
    }

    private void viewAuditLogs() {
        ConsoleUI.printSectionHeader("Audit Logs (Latest 20)");
        try {
            List<com.boardexam.model.AuditLog> logs = repo.getAuditLogs(20);
            if (logs.isEmpty()) { ConsoleUI.info("No logs yet."); return; }
            for (var log : logs) {
                ConsoleUI.println("  " + ConsoleUI.CYAN + log.getTimestamp() + ConsoleUI.RESET +
                    " | User#" + log.getUserId() + " | " + ConsoleUI.BOLD + log.getAction() +
                    ConsoleUI.RESET + " | " + log.getTargetEntity() + "#" + log.getTargetId() +
                    " | " + log.getDetail());
            }
        } catch (SQLException e) {
            ConsoleUI.error("Error fetching logs: " + e.getMessage());
        }
    }

    private void listAllExams() {
        ConsoleUI.printSectionHeader("All Scheduled Exams");
        try {
            List<Exam> exams10 = repo.getUpcomingExams(10);
            List<Exam> exams12 = repo.getUpcomingExams(12);

            ConsoleUI.bold("  CLASS 10 EXAMS:");
            printExamTable(exams10);
            ConsoleUI.println();
            ConsoleUI.bold("  CLASS 12 EXAMS:");
            printExamTable(exams12);

        } catch (SQLException e) {
            ConsoleUI.error("Error: " + e.getMessage());
        }
    }

    private void printExamTable(List<Exam> exams) {
        if (exams.isEmpty()) { ConsoleUI.info("  No upcoming exams."); return; }
        String[][] rows = new String[exams.size()][6];
        for (int i = 0; i < exams.size(); i++) {
            Exam e = exams.get(i);
            rows[i] = new String[]{
                String.valueOf(e.getExamId()), e.getSubjectCode(), e.getSubject(),
                e.getExamDate().toString(), e.getStartTime().toString(),
                e.getGradingType()
            };
        }
        ConsoleUI.printTable(new String[]{"ID", "Code", "Subject", "Date", "Time", "Mode"}, rows);
    }
}
