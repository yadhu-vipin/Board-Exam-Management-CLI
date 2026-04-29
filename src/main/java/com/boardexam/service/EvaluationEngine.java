package com.boardexam.service;

import com.boardexam.model.*;
import com.boardexam.repository.DataRepository;
import com.boardexam.util.ConsoleUI;
import com.boardexam.util.MaskIdGenerator;

import java.sql.SQLException;
import java.util.List;

/**
 * EvaluationEngine — OSM + Physical Compliance Business Logic
 *
 * Handles:
 *  1. Script generation + anonymization (Class 12)
 *  2. AE marking with section-wise validation (Class 10)
 *  3. HE random 10% review (CBSE policy)
 *  4. Coordinator finalization + variance check
 *  5. CBSE upload simulation
 */
public class EvaluationEngine {

    private final DataRepository repo;
    private final User currentUser;

    public EvaluationEngine(DataRepository repo, User currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCRIPT GENERATION (Admin / Coordinator)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates masked scripts for all students enrolled in an exam.
     * For Class 12: assigns a random BX-YYYY-XXXXXX masked ID.
     * For Class 10: uses the roll number directly (physical scripts).
     */
    public void generateScriptsForExam(int examId) {
        ConsoleUI.printSectionHeader("Generate Answer Scripts for Exam #" + examId);
        try {
            Exam exam = repo.findExamById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));

            List<User> students = repo.listStudentsByClass(exam.getClassLevel().getLevel());
            if (students.isEmpty()) {
                ConsoleUI.warn("No students found for Class " + exam.getClassLevel().getLevel());
                return;
            }

            int created = 0;
            for (User student : students) {
                String masked = exam.requiresOSM()
                    ? MaskIdGenerator.generate()
                    : "PHY-" + student.getRollNumber();

                Script s = new Script(0, student.getId(), masked,
                    examId, exam.getSubject(), exam.getClassLevel());
                repo.createScript(s);
                repo.writeAuditLog(currentUser.getId(), "SCRIPT_GENERATED",
                    "script", 0, "Student " + student.getRollNumber() + " → MaskedID: " + masked);
                created++;
            }

            ConsoleUI.success(created + " scripts generated for exam: " + exam.getSubject());
            if (exam.requiresOSM())
                ConsoleUI.info("Class 12 OSM: All student identities are anonymized.");
            else
                ConsoleUI.info("Class 10 Physical: Scripts use student roll numbers.");

        } catch (SQLException e) {
            ConsoleUI.error("DB error generating scripts: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AE MARKING
    // ─────────────────────────────────────────────────────────────────────────

    public void aeMarkingWorkflow(int examId) {
        if (!currentUser.canMarkScripts()) {
            ConsoleUI.error("Access Denied. Only AE/HE/Coordinator can mark scripts.");
            return;
        }
        ConsoleUI.printSectionHeader("AE Marking Portal — Exam #" + examId);
        ConsoleUI.warn("You will see ONLY Masked IDs. Student identity is hidden.");

        try {
            List<Script> pending = repo.getPendingScriptsForAE(examId);
            if (pending.isEmpty()) {
                ConsoleUI.info("No pending scripts for this exam.");
                return;
            }

            Exam exam = repo.findExamById(examId).orElseThrow();
            boolean isClass10 = exam.getClassLevel() == ClassLevel.CLASS_10;

            ConsoleUI.println("\n  Scripts to mark: " + pending.size());
            for (Script script : pending) {
                ConsoleUI.printDivider();
                ConsoleUI.bold("  Script Masked ID: " + ConsoleUI.CYAN + script.getMaskedId() + ConsoleUI.RESET);
                ConsoleUI.println("  Subject: " + script.getSubject());

                if (isClass10) {
                    markClass10Script(script, exam);
                } else {
                    markClass12Script(script, exam);
                }
            }
        } catch (SQLException e) {
            ConsoleUI.error("DB error during AE marking: " + e.getMessage());
        }
    }

    private void markClass10Script(Script script, Exam exam) throws SQLException {
        ConsoleUI.println("  Class 10 — Section-wise marking required:");
        ConsoleUI.println("  Total available: " + exam.getTotalMarks() + " marks");

        int maxPerSection = exam.getTotalMarks();
        int secA = ConsoleUI.promptInt("    Section A (MCQ) marks", 0, maxPerSection);
        int secB = ConsoleUI.promptInt("    Section B (Short Ans) marks", 0, maxPerSection);
        int secC = ConsoleUI.promptInt("    Section C (Long Ans) marks", 0, maxPerSection);
        int secD = ConsoleUI.promptInt("    Section D (Case/Map) marks", 0, maxPerSection);
        int total = secA + secB + secC + secD;

        // CBSE 2026 compliance: section sum must equal total
        if (total > exam.getTotalMarks()) {
            ConsoleUI.error("Section total " + total + " exceeds paper total " + exam.getTotalMarks() + ". Re-enter.");
            markClass10Script(script, exam); // recursive re-entry
            return;
        }

        ConsoleUI.println("  Computed total: " + total + " / " + exam.getTotalMarks());
        if (!ConsoleUI.promptYesNo("  Confirm marks?")) {
            ConsoleUI.warn("Skipped script " + script.getMaskedId());
            return;
        }

        repo.aeMarkScript(script.getScriptId(), currentUser.getId(),
            total, secA, secB, secC, secD);
        repo.writeAuditLog(currentUser.getId(), "AE_MARKS_ENTERED",
            "script", script.getScriptId(),
            "MaskedID=" + script.getMaskedId() + " Marks=" + total + " (A=" + secA + ",B=" + secB + ",C=" + secC + ",D=" + secD + ")");
        ConsoleUI.success("Marks saved for " + script.getMaskedId());
    }

    private void markClass12Script(Script script, Exam exam) throws SQLException {
        ConsoleUI.println("  Class 12 OSM — Enter total marks (no section breakdown required):");
        int marks = ConsoleUI.promptInt("  Total marks", 0, exam.getTotalMarks());

        if (!ConsoleUI.promptYesNo("  Confirm " + marks + " for " + script.getMaskedId() + "?")) {
            ConsoleUI.warn("Skipped.");
            return;
        }

        repo.aeMarkScript(script.getScriptId(), currentUser.getId(),
            marks, 0, 0, 0, 0);
        repo.writeAuditLog(currentUser.getId(), "AE_MARKS_ENTERED",
            "script", script.getScriptId(),
            "MaskedID=" + script.getMaskedId() + " Marks=" + marks);
        ConsoleUI.success("Marks saved for " + script.getMaskedId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HE REVIEW (10% Random Sample — CBSE Policy)
    // ─────────────────────────────────────────────────────────────────────────

    public void triggerHEFlagging(int examId) {
        if (currentUser.getRole() != UserRole.HEAD_EXAMINER &&
            currentUser.getRole() != UserRole.COORDINATOR &&
            currentUser.getRole() != UserRole.ADMIN) {
            ConsoleUI.error("Only HE, Coordinator, or Admin can trigger HE review flagging.");
            return;
        }
        try {
            repo.flagRandomScriptsForHEReview(examId);
            repo.writeAuditLog(currentUser.getId(), "HE_FLAGGING_TRIGGERED",
                "exam", examId, "Random 10% scripts flagged for HE review");
            ConsoleUI.success("10% of AE-marked scripts have been randomly flagged for HE review.");
        } catch (SQLException e) {
            ConsoleUI.error("Error during HE flagging: " + e.getMessage());
        }
    }

    public void heReviewWorkflow(int examId) {
        if (currentUser.getRole() != UserRole.HEAD_EXAMINER &&
            currentUser.getRole() != UserRole.COORDINATOR) {
            ConsoleUI.error("Access Denied.");
            return;
        }
        ConsoleUI.printSectionHeader("HE Review Portal — Exam #" + examId);

        try {
            List<Script> scripts = repo.getScriptsForHEReview(examId);
            if (scripts.isEmpty()) {
                ConsoleUI.info("No scripts pending HE review. Run 'Flag HE Scripts' first.");
                return;
            }

            ConsoleUI.println("  Scripts for HE review: " + scripts.size());
            for (Script script : scripts) {
                ConsoleUI.printDivider();
                ConsoleUI.bold("  Masked ID: " + script.getMaskedId());
                ConsoleUI.printKeyValue("AE Marks", String.valueOf(script.getAeMarks()));

                int heMarks = ConsoleUI.promptInt("  Your marks", 0, 100);

                // CBSE policy: if AE-HE variance > 5, warn
                int variance = Math.abs(script.getAeMarks() - heMarks);
                if (variance > 5) {
                    ConsoleUI.warn("Variance of " + variance + " detected (>5 mark policy).");
                    if (!ConsoleUI.promptYesNo("  Proceed anyway?")) {
                        repo.flagScriptAsDisputed(script.getScriptId(),
                            "HE variance: AE=" + script.getAeMarks() + " HE=" + heMarks);
                        ConsoleUI.warn("Script flagged as DISPUTED.");
                        continue;
                    }
                }

                if (!ConsoleUI.promptYesNo("  Confirm " + heMarks + " for " + script.getMaskedId() + "?")) continue;

                repo.heReviewScript(script.getScriptId(), currentUser.getId(), heMarks);
                repo.writeAuditLog(currentUser.getId(), "HE_MARKS_ENTERED",
                    "script", script.getScriptId(),
                    "MaskedID=" + script.getMaskedId() + " HE=" + heMarks + " AE was=" + script.getAeMarks());
                ConsoleUI.success("HE marks saved.");
            }
        } catch (Exception e) {
            ConsoleUI.error("Error during HE review: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COORDINATOR FINALIZATION
    // ─────────────────────────────────────────────────────────────────────────

    public void coordinatorFinalizeWorkflow(int examId) {
        if (currentUser.getRole() != UserRole.COORDINATOR &&
            currentUser.getRole() != UserRole.ADMIN) {
            ConsoleUI.error("Only Coordinator or Admin can finalize scripts.");
            return;
        }
        ConsoleUI.printSectionHeader("Coordinator Finalization — Exam #" + examId);
        ConsoleUI.warn("You are finalizing marks. This action is IRREVERSIBLE after upload.");

        try {
            List<Script> scripts = repo.getScriptsForCoordinator(examId);
            if (scripts.isEmpty()) {
                ConsoleUI.info("No scripts ready for finalization.");
                return;
            }

            ConsoleUI.println("  Scripts to finalize: " + scripts.size());
            String[][] rows = new String[scripts.size()][4];
            for (int i = 0; i < scripts.size(); i++) {
                Script s = scripts.get(i);
                rows[i] = new String[]{
                    s.getMaskedId(), s.getStatus().name(),
                    String.valueOf(s.getAeMarks()),
                    s.isHeReviewNeeded() ? String.valueOf(s.getHeMarks()) : "N/A"
                };
            }
            ConsoleUI.printTable(new String[]{"Masked ID", "Status", "AE Marks", "HE Marks"}, rows);

            if (!ConsoleUI.promptYesNo("  Finalize ALL listed scripts with effective marks?")) return;

            int finalized = 0;
            for (Script script : scripts) {
                int effectiveMarks = script.isHeReviewNeeded() && script.getHeMarks() > 0
                    ? script.getHeMarks()
                    : script.getAeMarks();
                repo.coordinatorFinalize(script.getScriptId(), currentUser.getId(), effectiveMarks);
                finalized++;
            }

            repo.writeAuditLog(currentUser.getId(), "COORDINATOR_FINALIZED",
                "exam", examId, finalized + " scripts finalized");
            ConsoleUI.success(finalized + " scripts finalized successfully.");

        } catch (Exception e) {
            ConsoleUI.error("Finalization error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CBSE PORTAL UPLOAD SIMULATION
    // ─────────────────────────────────────────────────────────────────────────

    public void simulatePortalUpload(int examId) {
        if (currentUser.getRole() != UserRole.COORDINATOR &&
            currentUser.getRole() != UserRole.ADMIN) {
            ConsoleUI.error("Only Coordinator can upload to CBSE portal.");
            return;
        }
        ConsoleUI.printSectionHeader("CBSE Central Portal Upload — Exam #" + examId);
        try {
            int[] stats = repo.getScriptStats(examId);
            // stats: [total, pending, ae_marked, he_reviewed, verified, finalized, uploaded]
            int readyToUpload = stats[4]; // COORDINATOR_VERIFIED

            if (readyToUpload == 0) {
                ConsoleUI.warn("No scripts in COORDINATOR_VERIFIED state. Finalize first.");
                return;
            }

            ConsoleUI.printKeyValue("Ready for upload", String.valueOf(readyToUpload));
            ConsoleUI.warn("Simulating upload to https://cbseportal.nic.in/osm ...");

            // Simulate upload for each verified script
            List<Script> scripts = repo.getScriptsForCoordinator(examId);
            for (Script s : scripts) {
                if (s.getStatus() == ScriptStatus.COORDINATOR_VERIFIED) {
                    repo.uploadScript(s.getScriptId());
                }
            }

            // Re-fetch for verified ones that just moved to UPLOADED
            ConsoleUI.success("Upload complete. " + readyToUpload + " scripts marked as UPLOADED.");
            ConsoleUI.info("[SIMULATED] In production: digitally signed JSON payload sent to CBSE NIC servers.");

            repo.writeAuditLog(currentUser.getId(), "PORTAL_UPLOAD",
                "exam", examId, readyToUpload + " scripts uploaded");

        } catch (SQLException e) {
            ConsoleUI.error("Upload error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATS DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────

    public void showEvaluationStats(int examId) {
        ConsoleUI.printSectionHeader("Evaluation Progress — Exam #" + examId);
        try {
            int[] stats = repo.getScriptStats(examId);
            String[] labels = {"Total Scripts", "Pending", "AE Marked", "HE Reviewed",
                               "Coordinator Verified", "Finalized", "Uploaded"};
            ConsoleUI.println();
            for (int i = 0; i < labels.length; i++) {
                String bar = "█".repeat(Math.max(0, stats[i]));
                ConsoleUI.println("  " + ConsoleUI.padRight(labels[i] + ":", 28) +
                    ConsoleUI.CYAN + stats[i] + ConsoleUI.RESET);
            }
            if (stats[0] > 0) {
                int uploaded = stats[6];
                int pct = (uploaded * 100) / stats[0];
                ConsoleUI.println("\n  Overall completion: " + ConsoleUI.GREEN + pct + "%" + ConsoleUI.RESET);
            }
        } catch (SQLException e) {
            ConsoleUI.error("Error fetching stats: " + e.getMessage());
        }
    }
}
