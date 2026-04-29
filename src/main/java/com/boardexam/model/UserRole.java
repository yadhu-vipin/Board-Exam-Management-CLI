package com.boardexam.model;

/**
 * CBSE-defined roles for the examination system.
 * Aligns with CBSE 2026 staffing hierarchy.
 */
public enum UserRole {
    STUDENT,
    INVIGILATOR,
    ASSISTANT_EXAMINER,   // AE: First-level marker
    HEAD_EXAMINER,        // HE: Reviews 10% of AE scripts
    COORDINATOR,          // Regional: Finalizes and uploads
    ADMIN                 // System administrator
}
