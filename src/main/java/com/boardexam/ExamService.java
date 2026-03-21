package com.boardexam;
import java.sql.*;

public class ExamService {
    
    // Logic for Conflict-Free Invigilation Duty
    public void assignDuty(int teacherId, String center, String slot) {
        String query = "INSERT INTO Assignments (teacher_id, exam_center, time_slot) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getConnection(); 
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setInt(1, teacherId);
            pstmt.setString(2, center);
            pstmt.setString(3, slot);
            pstmt.executeUpdate();
            System.out.println("[Success] Duty assigned to Teacher #" + teacherId + " at " + center);
            
        } catch (SQLException e) {
            System.err.println("[Conflict] Error: Teacher might already have a duty in slot: " + slot);
        }
    }

    // Logic for Post-Exam Script Routing
    public void routeScripts(String subject) {
        System.out.println("[Routing] Analyzing answer script logs for: " + subject);
        String destination = switch (subject.toLowerCase()) {
            case "cs" -> "Regional Center A (Trivandrum)";
            case "physics" -> "Regional Center B (Kochi)";
            default -> "Main Administrative Hub (Amritapuri)";
        };
        System.out.println("[Result] All " + subject + " scripts routed to: " + destination);
    }
}