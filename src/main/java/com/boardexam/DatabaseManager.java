package com.boardexam;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;

public class DatabaseManager {
    // Inside DatabaseManager.java
    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    // It will look for a variable named DB_PASSWORD
    private static final String PASSWORD = System.getenv("DB_PASSWORD");

    public static Connection getConnection() throws SQLException {
        if (PASSWORD == null) {
            throw new SQLException("Environment variable DB_PASSWORD is not set!");
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }


    public static void setupDatabase() {
        String schema = """
                CREATE TABLE IF NOT EXISTS Teachers (
                    id SERIAL PRIMARY KEY,
                    name TEXT NOT NULL,
                    subject TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS Assignments (
                    id SERIAL PRIMARY KEY,
                    teacher_id INT REFERENCES Teachers(id),
                    exam_center TEXT NOT NULL,
                    time_slot TEXT NOT NULL,
                    UNIQUE(teacher_id, time_slot)
                );
                """;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(schema);
            System.out.println("[DB] Schema initialized successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}