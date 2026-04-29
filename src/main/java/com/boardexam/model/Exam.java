package com.boardexam.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Represents a scheduled CBSE examination.
 * 
 * NORMALIZATION (3NF):
 * - centerName is not functionally dependent on subject (separate entity in full schema).
 * - gradingType derived from classLevel — kept here for denormalized query performance
 *   but enforced via CHECK constraint in DB.
 */
public class Exam {
    private int examId;
    private String subject;
    private String subjectCode;      // CBSE official subject code (e.g., 301 = English Core)
    private LocalDate examDate;
    private LocalTime startTime;
    private int durationMinutes;     // Typically 180 (3 hrs) or 120 for some papers
    private String centerName;
    private String centerCode;       // CBSE Center code (e.g., DEL-0021)
    private ClassLevel classLevel;
    private int totalMarks;          // Theory marks (e.g., 80 for Class 12)
    private int practicalMarks;      // Practical/IA marks (e.g., 20 for Class 12)
    private boolean osmEnabled;      // True only for Class 12 theory papers

    public Exam() {}

    public Exam(int examId, String subject, String subjectCode, LocalDate examDate,
                LocalTime startTime, int durationMinutes, String centerName,
                String centerCode, ClassLevel classLevel, int totalMarks,
                int practicalMarks) {
        this.examId          = examId;
        this.subject         = subject;
        this.subjectCode     = subjectCode;
        this.examDate        = examDate;
        this.startTime       = startTime;
        this.durationMinutes = durationMinutes;
        this.centerName      = centerName;
        this.centerCode      = centerCode;
        this.classLevel      = classLevel;
        this.totalMarks      = totalMarks;
        this.practicalMarks  = practicalMarks;
        this.osmEnabled      = (classLevel == ClassLevel.CLASS_12);
    }

    // ──────────────────────────────────────────────
    // Business Logic
    // ──────────────────────────────────────────────

    public LocalTime getEndTime() {
        return startTime.plusMinutes(durationMinutes);
    }

    public int getFullMarks() {
        return totalMarks + practicalMarks;
    }

    public String getGradingType() {
        return classLevel.getGradingType();
    }

    public boolean isPracticalPaper() {
        return practicalMarks > 0;
    }

    /** CBSE 2026: Class 12 papers above 40 marks go through OSM */
    public boolean requiresOSM() {
        return osmEnabled && totalMarks >= 40;
    }

    // ──────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────

    public int getExamId()                        { return examId; }
    public void setExamId(int e)                  { this.examId = e; }
    public String getSubject()                    { return subject; }
    public void setSubject(String s)              { this.subject = s; }
    public String getSubjectCode()                { return subjectCode; }
    public void setSubjectCode(String c)          { this.subjectCode = c; }
    public LocalDate getExamDate()                { return examDate; }
    public void setExamDate(LocalDate d)          { this.examDate = d; }
    public LocalTime getStartTime()               { return startTime; }
    public void setStartTime(LocalTime t)         { this.startTime = t; }
    public int getDurationMinutes()               { return durationMinutes; }
    public void setDurationMinutes(int d)         { this.durationMinutes = d; }
    public String getCenterName()                 { return centerName; }
    public void setCenterName(String c)           { this.centerName = c; }
    public String getCenterCode()                 { return centerCode; }
    public void setCenterCode(String c)           { this.centerCode = c; }
    public ClassLevel getClassLevel()             { return classLevel; }
    public void setClassLevel(ClassLevel cl)      { this.classLevel = cl; }
    public int getTotalMarks()                    { return totalMarks; }
    public void setTotalMarks(int m)              { this.totalMarks = m; }
    public int getPracticalMarks()                { return practicalMarks; }
    public void setPracticalMarks(int m)          { this.practicalMarks = m; }
    public boolean isOsmEnabled()                 { return osmEnabled; }
    public void setOsmEnabled(boolean b)          { this.osmEnabled = b; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Exam e)) return false;
        return examId == e.examId;
    }

    @Override
    public int hashCode() { return Objects.hash(examId); }

    @Override
    public String toString() {
        return "Exam{id=%d, subject='%s'(%s), date=%s, center='%s', class=%s}"
                .formatted(examId, subject, subjectCode, examDate,
                           centerName, classLevel.getLevel());
    }
}
