package com.boardexam.model;

import java.time.LocalDateTime;

/**
 * Immutable audit record — every critical action is logged.
 * Supports forensic review and CBSE compliance auditing.
 * AuditLogs are APPEND-ONLY (no UPDATE/DELETE allowed on this table).
 */
public final class AuditLog {
    private final int logId;
    private final int userId;
    private final String action;        // e.g. "MARKS_ENTERED", "SCRIPT_FINALIZED"
    private final String targetEntity;  // e.g. "script", "exam", "user"
    private final int targetId;
    private final String detail;        // Human-readable detail
    private final LocalDateTime timestamp;
    private final String ipAddress;

    public AuditLog(int logId, int userId, String action, String targetEntity,
                    int targetId, String detail, LocalDateTime timestamp, String ipAddress) {
        this.logId        = logId;
        this.userId       = userId;
        this.action       = action;
        this.targetEntity = targetEntity;
        this.targetId     = targetId;
        this.detail       = detail;
        this.timestamp    = timestamp;
        this.ipAddress    = ipAddress;
    }

    public int getLogId()              { return logId; }
    public int getUserId()             { return userId; }
    public String getAction()          { return action; }
    public String getTargetEntity()    { return targetEntity; }
    public int getTargetId()           { return targetId; }
    public String getDetail()          { return detail; }
    public LocalDateTime getTimestamp(){ return timestamp; }
    public String getIpAddress()       { return ipAddress; }

    @Override
    public String toString() {
        return "[%s] User#%d | %s on %s#%d | %s"
                .formatted(timestamp, userId, action, targetEntity, targetId, detail);
    }
}
