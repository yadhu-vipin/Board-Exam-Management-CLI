package com.boardexam.model;

/**
 * Lifecycle states of an answer script in the CBSE pipeline.
 * PENDING -> AE_MARKED -> HE_REVIEWED -> COORDINATOR_VERIFIED -> FINALIZED -> UPLOADED
 */
public enum ScriptStatus {
    PENDING("Awaiting AE Assignment"),
    AE_MARKED("Marked by Assistant Examiner"),
    HE_REVIEWED("Reviewed by Head Examiner"),
    COORDINATOR_VERIFIED("Verified by Coordinator"),
    FINALIZED("Finalized — ready for upload"),
    UPLOADED("Uploaded to CBSE Central Portal"),
    DISPUTED("Flagged for re-evaluation");

    private final String description;

    ScriptStatus(String description) { this.description = description; }
    public String getDescription()   { return description; }
}
