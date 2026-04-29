package com.boardexam.repository;

import com.boardexam.DatabaseManager;
import com.boardexam.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DataRepository — Data Access Object (DAO) layer.
 *
 * ALL SQL lives here. Services never touch raw JDBC.
 * Uses PreparedStatements throughout to prevent SQL Injection.
 *
 * ACID COMPLIANCE:
 *   Atomicity  — multi-step ops use BEGIN/COMMIT with explicit ROLLBACK on error.
 *   Consistency— DB CHECK constraints + app-layer state-machine guard transitions.
 *   Isolation  — READ_COMMITTED default; SERIALIZABLE for mark finalization.
 *   Durability — PostgreSQL WAL ensures committed data survives crashes.
 */
public class DataRepository {

    private static final Logger log = LoggerFactory.getLogger(DataRepository.class);
    private final DatabaseManager db;

    public DataRepository(DatabaseManager db) { this.db = db; }

    // ════════════════════════════════════ USER ════════════════════════════════════

    public Optional<User> findUserByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ? AND is_active = TRUE";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(mapUser(rs)); }
        }
        return Optional.empty();
    }

    public Optional<User> findUserByRollNumber(String rollNumber) throws SQLException {
        String sql = "SELECT * FROM users WHERE roll_number = ? AND is_active = TRUE";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, rollNumber);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(mapUser(rs)); }
        }
        return Optional.empty();
    }

    public Optional<User> findUserById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(mapUser(rs)); }
        }
        return Optional.empty();
    }

    public int createUser(User user) throws SQLException {
        String sql = "INSERT INTO users (name,email,password_hash,role,class_level,roll_number,staff_code,is_active) VALUES (?,?,?,?,?,?,?,TRUE) RETURNING id";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            if (user.getClassLevel() != null) ps.setInt(5, user.getClassLevel().getLevel());
            else ps.setNull(5, Types.INTEGER);
            ps.setString(6, user.getRollNumber());
            ps.setString(7, user.getStaffCode());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public void updateLastLogin(int userId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET last_login=NOW() WHERE id=?")) {
            ps.setInt(1, userId); ps.executeUpdate();
        }
    }

    public List<User> listStudentsByClass(int classLevel) throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role='STUDENT' AND class_level=? AND is_active=TRUE ORDER BY roll_number";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, classLevel);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapUser(rs)); }
        }
        return list;
    }

    // ════════════════════════════════════ EXAM ════════════════════════════════════

    public List<Exam> getUpcomingExams(int classLevel) throws SQLException {
        String sql = "SELECT e.*,ec.center_name,ec.center_code FROM exams e LEFT JOIN exam_centers ec ON e.center_id=ec.center_id WHERE e.class_level=? AND e.exam_date>=CURRENT_DATE ORDER BY e.exam_date,e.start_time";
        List<Exam> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, classLevel);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapExam(rs)); }
        }
        return list;
    }

    public Optional<Exam> findExamById(int examId) throws SQLException {
        String sql = "SELECT e.*,ec.center_name,ec.center_code FROM exams e LEFT JOIN exam_centers ec ON e.center_id=ec.center_id WHERE e.exam_id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(mapExam(rs)); }
        }
        return Optional.empty();
    }

    public int createExam(Exam exam) throws SQLException {
        String sql = "INSERT INTO exams (subject,subject_code,exam_date,start_time,duration_minutes,class_level,total_marks,practical_marks,osm_enabled) VALUES (?,?,?,?,?,?,?,?,?) RETURNING exam_id";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, exam.getSubject()); ps.setString(2, exam.getSubjectCode());
            ps.setDate(3, Date.valueOf(exam.getExamDate())); ps.setTime(4, Time.valueOf(exam.getStartTime()));
            ps.setInt(5, exam.getDurationMinutes()); ps.setInt(6, exam.getClassLevel().getLevel());
            ps.setInt(7, exam.getTotalMarks()); ps.setInt(8, exam.getPracticalMarks());
            ps.setBoolean(9, exam.isOsmEnabled());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public List<Exam> getExamsForStudent(int studentId) throws SQLException {
        String sql = "SELECT e.*,ec.center_name,ec.center_code FROM exams e JOIN student_exam_enrollment see ON e.exam_id=see.exam_id LEFT JOIN exam_centers ec ON e.center_id=ec.center_id WHERE see.student_id=? ORDER BY e.exam_date";
        List<Exam> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapExam(rs)); }
        }
        return list;
    }

    // ════════════════════════════════════ SCRIPT ══════════════════════════════════

    public int createScript(Script script) throws SQLException {
        String sql = "INSERT INTO scripts (student_id,masked_id,exam_id,subject,class_level,status) VALUES (?,?,?,?,?,'PENDING') RETURNING script_id";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, script.getStudentId()); ps.setString(2, script.getMaskedId());
            ps.setInt(3, script.getExamId()); ps.setString(4, script.getSubject());
            ps.setInt(5, script.getClassLevel().getLevel());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public void aeMarkScript(int scriptId, int aeStaffId, int aeMarks,
                              int secA, int secB, int secC, int secD) throws SQLException {
        String sql = "UPDATE scripts SET ae_marks=?,ae_staff_id=?,ae_marked_at=NOW(),section_a_marks=?,section_b_marks=?,section_c_marks=?,section_d_marks=?,status='AE_MARKED' WHERE script_id=? AND status='PENDING'";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1,aeMarks); ps.setInt(2,aeStaffId); ps.setInt(3,secA);
            ps.setInt(4,secB); ps.setInt(5,secC); ps.setInt(6,secD); ps.setInt(7,scriptId);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Script not in PENDING state.");
        }
    }

    public void heReviewScript(int scriptId, int heStaffId, int heMarks) throws SQLException {
        String sql = "UPDATE scripts SET he_marks=?,he_staff_id=?,he_reviewed_at=NOW(),status='HE_REVIEWED' WHERE script_id=? AND status='AE_MARKED' AND he_review_needed=TRUE";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1,heMarks); ps.setInt(2,heStaffId); ps.setInt(3,scriptId);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Script not eligible for HE review.");
        }
    }

    /** SERIALIZABLE transaction for finalization — prevents phantom reads */
    public void coordinatorFinalize(int scriptId, int coordinatorId, int finalMarks) throws SQLException {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                try (PreparedStatement lock = c.prepareStatement("SELECT script_id FROM scripts WHERE script_id=? FOR UPDATE")) {
                    lock.setInt(1, scriptId); lock.executeQuery();
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE scripts SET final_marks=?,coordinator_id=?,finalized_at=NOW(),status='COORDINATOR_VERIFIED' WHERE script_id=? AND status IN ('AE_MARKED','HE_REVIEWED')")) {
                    ps.setInt(1,finalMarks); ps.setInt(2,coordinatorId); ps.setInt(3,scriptId);
                    if (ps.executeUpdate() == 0) throw new IllegalStateException("Script "+scriptId+" not ready for finalization.");
                }
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    public void uploadScript(int scriptId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE scripts SET status='UPLOADED',finalized_at=NOW() WHERE script_id=? AND status='COORDINATOR_VERIFIED'")) {
            ps.setInt(1,scriptId); ps.executeUpdate();
        }
    }

    public void flagScriptAsDisputed(int scriptId, String reason) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE scripts SET status='DISPUTED',dispute_reason=? WHERE script_id=?")) {
            ps.setString(1,reason); ps.setInt(2,scriptId); ps.executeUpdate();
        }
    }

    public void flagRandomScriptsForHEReview(int examId) throws SQLException {
        String sql = "UPDATE scripts SET he_review_needed=TRUE WHERE exam_id=? AND status='AE_MARKED' AND script_id IN (SELECT script_id FROM scripts WHERE exam_id=? AND status='AE_MARKED' ORDER BY RANDOM() LIMIT GREATEST(1,(SELECT COUNT(*)*0.1 FROM scripts WHERE exam_id=? AND status='AE_MARKED')::INT))";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1,examId); ps.setInt(2,examId); ps.setInt(3,examId);
            int flagged = ps.executeUpdate();
            log.info("Flagged {} scripts for HE review (exam_id={})", flagged, examId);
        }
    }

    public List<Script> getPendingScriptsForAE(int examId) throws SQLException {
        return fetchScriptsAnon("SELECT script_id,masked_id,exam_id,subject,class_level,status,ae_marks,he_marks,final_marks,he_review_needed,section_a_marks,section_b_marks,section_c_marks,section_d_marks,ae_staff_id,he_staff_id,coordinator_id,ae_marked_at,he_reviewed_at,finalized_at,dispute_reason,0 AS student_id FROM scripts WHERE exam_id=? AND status='PENDING' ORDER BY masked_id", examId);
    }

    public List<Script> getScriptsForHEReview(int examId) throws SQLException {
        return fetchScriptsAnon("SELECT script_id,masked_id,exam_id,subject,class_level,status,ae_marks,he_marks,final_marks,he_review_needed,section_a_marks,section_b_marks,section_c_marks,section_d_marks,ae_staff_id,he_staff_id,coordinator_id,ae_marked_at,he_reviewed_at,finalized_at,dispute_reason,0 AS student_id FROM scripts WHERE exam_id=? AND he_review_needed=TRUE AND status='AE_MARKED' ORDER BY masked_id", examId);
    }

    public List<Script> getScriptsForCoordinator(int examId) throws SQLException {
        return fetchScriptsAnon("SELECT *,student_id FROM scripts WHERE exam_id=? AND status IN ('AE_MARKED','HE_REVIEWED') ORDER BY masked_id", examId);
    }

    private List<Script> fetchScriptsAnon(String sql, int examId) throws SQLException {
        List<Script> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapScript(rs)); }
        }
        return list;
    }

    public Optional<Script> findScriptByMaskedId(String maskedId) throws SQLException {
        String sql = "SELECT script_id,masked_id,exam_id,subject,class_level,status,ae_marks,he_marks,final_marks,he_review_needed,section_a_marks,section_b_marks,section_c_marks,section_d_marks,ae_staff_id,he_staff_id,coordinator_id,ae_marked_at,he_reviewed_at,finalized_at,dispute_reason,0 AS student_id FROM scripts WHERE masked_id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, maskedId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(mapScript(rs)); }
        }
        return Optional.empty();
    }

    public void enrollStudent(int studentId, int examId, String seatNumber) throws SQLException {
        String sql = "INSERT INTO student_exam_enrollment (student_id,exam_id,seat_number) VALUES (?,?,?) ON CONFLICT (student_id,exam_id) DO NOTHING";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1,studentId); ps.setInt(2,examId); ps.setString(3,seatNumber); ps.executeUpdate();
        }
    }

    // ════════════════════════════════════ AUDIT ═══════════════════════════════════

    public void writeAuditLog(int userId, String action, String entity, int targetId, String detail) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO audit_logs (user_id,action,target_entity,target_id,detail) VALUES (?,?,?,?,?)")) {
            ps.setString(1, String.valueOf(userId)); ps.setString(2,action);
            ps.setString(3,entity); ps.setString(4, String.valueOf(targetId)); ps.setString(5,detail);
            ps.setInt(1,userId); ps.setString(2,action); ps.setString(3,entity);
            ps.setInt(4,targetId); ps.setString(5,detail);
            ps.executeUpdate();
        }
    }

    public List<AuditLog> getAuditLogs(int limit) throws SQLException {
        List<AuditLog> logs = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT ?")) {
            ps.setInt(1,limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new AuditLog(rs.getInt("log_id"),rs.getInt("user_id"),
                        rs.getString("action"),rs.getString("target_entity"),
                        rs.getInt("target_id"),rs.getString("detail"),
                        rs.getTimestamp("timestamp").toLocalDateTime(),
                        rs.getString("ip_address")));
                }
            }
        }
        return logs;
    }

    public int[] getScriptStats(int examId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE status='PENDING') AS pending, COUNT(*) FILTER (WHERE status='AE_MARKED') AS ae_marked, COUNT(*) FILTER (WHERE status='HE_REVIEWED') AS he_reviewed, COUNT(*) FILTER (WHERE status='COORDINATOR_VERIFIED') AS verified, COUNT(*) FILTER (WHERE status='FINALIZED') AS finalized, COUNT(*) FILTER (WHERE status='UPLOADED') AS uploaded FROM scripts WHERE exam_id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1,examId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new int[]{rs.getInt("total"),rs.getInt("pending"),rs.getInt("ae_marked"),rs.getInt("he_reviewed"),rs.getInt("verified"),rs.getInt("finalized"),rs.getInt("uploaded")};
            }
        }
    }

    // ════════════════════════════════════ MAPPERS ══════════════════════════════════

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id")); u.setName(rs.getString("name")); u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash")); u.setRole(UserRole.valueOf(rs.getString("role")));
        int cl = rs.getInt("class_level");
        if (!rs.wasNull()) u.setClassLevel(ClassLevel.fromInt(cl));
        u.setRollNumber(rs.getString("roll_number")); u.setStaffCode(rs.getString("staff_code"));
        u.setActive(rs.getBoolean("is_active"));
        Timestamp ca = rs.getTimestamp("created_at"); if (ca!=null) u.setCreatedAt(ca.toLocalDateTime());
        Timestamp ll = rs.getTimestamp("last_login"); if (ll!=null) u.setLastLogin(ll.toLocalDateTime());
        return u;
    }

    private Exam mapExam(ResultSet rs) throws SQLException {
        Exam e = new Exam(); e.setExamId(rs.getInt("exam_id")); e.setSubject(rs.getString("subject"));
        e.setSubjectCode(rs.getString("subject_code")); e.setExamDate(rs.getDate("exam_date").toLocalDate());
        e.setStartTime(rs.getTime("start_time").toLocalTime()); e.setDurationMinutes(rs.getInt("duration_minutes"));
        e.setClassLevel(ClassLevel.fromInt(rs.getInt("class_level"))); e.setTotalMarks(rs.getInt("total_marks"));
        e.setPracticalMarks(rs.getInt("practical_marks")); e.setOsmEnabled(rs.getBoolean("osm_enabled"));
        try { e.setCenterName(rs.getString("center_name")); } catch (SQLException ignored) {}
        try { e.setCenterCode(rs.getString("center_code")); } catch (SQLException ignored) {}
        return e;
    }

    private Script mapScript(ResultSet rs) throws SQLException {
        Script s = new Script(); s.setScriptId(rs.getInt("script_id")); s.setStudentId(rs.getInt("student_id"));
        s.setMaskedId(rs.getString("masked_id")); s.setExamId(rs.getInt("exam_id"));
        s.setSubject(rs.getString("subject")); s.setClassLevel(ClassLevel.fromInt(rs.getInt("class_level")));
        s.setStatus(ScriptStatus.valueOf(rs.getString("status"))); s.setAeMarks(rs.getInt("ae_marks"));
        s.setHeMarks(rs.getInt("he_marks")); s.setFinalMarks(rs.getInt("final_marks"));
        s.setHeReviewNeeded(rs.getBoolean("he_review_needed")); s.setSectionAMarks(rs.getInt("section_a_marks"));
        s.setSectionBMarks(rs.getInt("section_b_marks")); s.setSectionCMarks(rs.getInt("section_c_marks"));
        s.setSectionDMarks(rs.getInt("section_d_marks")); s.setAeStaffId(rs.getInt("ae_staff_id"));
        s.setHeStaffId(rs.getInt("he_staff_id")); s.setCoordinatorId(rs.getInt("coordinator_id"));
        Timestamp ae=rs.getTimestamp("ae_marked_at"); if(ae!=null) s.setAeMarkedAt(ae.toLocalDateTime());
        Timestamp he=rs.getTimestamp("he_reviewed_at"); if(he!=null) s.setHeReviewedAt(he.toLocalDateTime());
        Timestamp fin=rs.getTimestamp("finalized_at"); if(fin!=null) s.setFinalizedAt(fin.toLocalDateTime());
        s.setDisputeReason(rs.getString("dispute_reason"));
        return s;
    }
}
