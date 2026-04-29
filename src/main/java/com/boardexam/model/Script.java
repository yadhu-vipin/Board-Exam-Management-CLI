package com.boardexam.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an answer script in the CBSE evaluation pipeline.
 * 
 * KEY DESIGN (Anonymization):
 * - maskedId is a random UUID-based code. Evaluators ONLY see maskedId.
 * - studentId (the link to actual student) is accessible ONLY by COORDINATOR role.
 * - This implements CBSE's "False Roll Number" policy for Class 12 OSM.
 * 
 * ACID COMPLIANCE NOTES:
 * - All mark updates happen inside a TRANSACTION (Atomicity).
 * - Status transitions are sequential and validated (Consistency).
 * - Row-level locks prevent concurrent mark overwriting (Isolation: READ_COMMITTED).
 * - All changes are WAL-logged in PostgreSQL (Durability).
 */
public class Script {
    private int scriptId;
    private int studentId;       // FK → users.id — HIDDEN from evaluators
    private String maskedId;     // e.g. "BX-2026-A9F3" — the only ID shown to AE/HE
    private int examId;          // FK → exams.exam_id
    private String subject;
    private ClassLevel classLevel;
    private ScriptStatus status;

    // Marks (multi-layer)
    private int aeMarks;           // Assistant Examiner marks
    private int heMarks;           // Head Examiner marks (for 10% sample)
    private int finalMarks;        // Coordinator-approved final marks
    private boolean heReviewNeeded;// True if HE randomly selects this script

    // Section-wise marks (Class 10 compliance)
    private int sectionAMarks;     // e.g. Section A: MCQ
    private int sectionBMarks;     // Section B: Short Answer
    private int sectionCMarks;     // Section C: Long Answer/Case Study
    private int sectionDMarks;     // Section D: Source-based / Map (where applicable)

    // Audit trail
    private int aeStaffId;
    private int heStaffId;
    private int coordinatorId;
    private LocalDateTime aeMarkedAt;
    private LocalDateTime heReviewedAt;
    private LocalDateTime finalizedAt;
    private String disputeReason;

    public Script() {}

    public Script(int scriptId, int studentId, String maskedId, int examId,
                  String subject, ClassLevel classLevel) {
        this.scriptId   = scriptId;
        this.studentId  = studentId;
        this.maskedId   = maskedId;
        this.examId     = examId;
        this.subject    = subject;
        this.classLevel = classLevel;
        this.status     = ScriptStatus.PENDING;
    }

    // ──────────────────────────────────────────────
    // Business Logic
    // ──────────────────────────────────────────────

    /** Validates section-wise totals match the entered total (Class 10 compliance) */
    public boolean isSectionWiseValid(int declaredTotal) {
        int sectionSum = sectionAMarks + sectionBMarks + sectionCMarks + sectionDMarks;
        return sectionSum == declaredTotal;
    }

    /** For Class 12 HE review: checks if AE-to-HE variance exceeds ±5 marks */
    public boolean hasSignificantVariance() {
        if (heReviewNeeded && heMarks > 0) {
            return Math.abs(aeMarks - heMarks) > 5;
        }
        return false;
    }

    public int getEffectiveMarks() {
        if (status == ScriptStatus.COORDINATOR_VERIFIED || status == ScriptStatus.FINALIZED
                || status == ScriptStatus.UPLOADED) {
            return finalMarks;
        }
        if (heReviewNeeded && heMarks > 0) return heMarks;
        return aeMarks;
    }

    /** State machine transition validation */
    public boolean canTransitionTo(ScriptStatus next) {
        return switch (status) {
            case PENDING             -> next == ScriptStatus.AE_MARKED;
            case AE_MARKED           -> next == ScriptStatus.HE_REVIEWED
                                         || next == ScriptStatus.COORDINATOR_VERIFIED
                                         || next == ScriptStatus.DISPUTED;
            case HE_REVIEWED         -> next == ScriptStatus.COORDINATOR_VERIFIED
                                         || next == ScriptStatus.DISPUTED;
            case COORDINATOR_VERIFIED-> next == ScriptStatus.FINALIZED;
            case FINALIZED           -> next == ScriptStatus.UPLOADED;
            case DISPUTED            -> next == ScriptStatus.AE_MARKED; // Re-mark
            case UPLOADED            -> false; // Terminal state
        };
    }

    // ──────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────

    public int getScriptId()                          { return scriptId; }
    public void setScriptId(int s)                    { this.scriptId = s; }
    public int getStudentId()                         { return studentId; }
    public void setStudentId(int s)                   { this.studentId = s; }
    public String getMaskedId()                       { return maskedId; }
    public void setMaskedId(String m)                 { this.maskedId = m; }
    public int getExamId()                            { return examId; }
    public void setExamId(int e)                      { this.examId = e; }
    public String getSubject()                        { return subject; }
    public void setSubject(String s)                  { this.subject = s; }
    public ClassLevel getClassLevel()                 { return classLevel; }
    public void setClassLevel(ClassLevel cl)          { this.classLevel = cl; }
    public ScriptStatus getStatus()                   { return status; }
    public void setStatus(ScriptStatus s)             { this.status = s; }
    public int getAeMarks()                           { return aeMarks; }
    public void setAeMarks(int m)                     { this.aeMarks = m; }
    public int getHeMarks()                           { return heMarks; }
    public void setHeMarks(int m)                     { this.heMarks = m; }
    public int getFinalMarks()                        { return finalMarks; }
    public void setFinalMarks(int m)                  { this.finalMarks = m; }
    public boolean isHeReviewNeeded()                 { return heReviewNeeded; }
    public void setHeReviewNeeded(boolean b)          { this.heReviewNeeded = b; }
    public int getSectionAMarks()                     { return sectionAMarks; }
    public void setSectionAMarks(int m)               { this.sectionAMarks = m; }
    public int getSectionBMarks()                     { return sectionBMarks; }
    public void setSectionBMarks(int m)               { this.sectionBMarks = m; }
    public int getSectionCMarks()                     { return sectionCMarks; }
    public void setSectionCMarks(int m)               { this.sectionCMarks = m; }
    public int getSectionDMarks()                     { return sectionDMarks; }
    public void setSectionDMarks(int m)               { this.sectionDMarks = m; }
    public int getAeStaffId()                         { return aeStaffId; }
    public void setAeStaffId(int i)                   { this.aeStaffId = i; }
    public int getHeStaffId()                         { return heStaffId; }
    public void setHeStaffId(int i)                   { this.heStaffId = i; }
    public int getCoordinatorId()                     { return coordinatorId; }
    public void setCoordinatorId(int i)               { this.coordinatorId = i; }
    public LocalDateTime getAeMarkedAt()              { return aeMarkedAt; }
    public void setAeMarkedAt(LocalDateTime t)        { this.aeMarkedAt = t; }
    public LocalDateTime getHeReviewedAt()            { return heReviewedAt; }
    public void setHeReviewedAt(LocalDateTime t)      { this.heReviewedAt = t; }
    public LocalDateTime getFinalizedAt()             { return finalizedAt; }
    public void setFinalizedAt(LocalDateTime t)       { this.finalizedAt = t; }
    public String getDisputeReason()                  { return disputeReason; }
    public void setDisputeReason(String r)            { this.disputeReason = r; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Script s)) return false;
        return scriptId == s.scriptId;
    }

    @Override
    public int hashCode() { return Objects.hash(scriptId); }

    @Override
    public String toString() {
        return "Script{maskedId='%s', subject='%s', status=%s, effectiveMarks=%d}"
                .formatted(maskedId, subject, status, getEffectiveMarks());
    }
}
