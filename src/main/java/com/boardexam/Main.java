package com.boardexam;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        DatabaseManager.setupDatabase();
        ExamService service = new ExamService();
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- Board Exam Management System (v2.0 Java Backend) ---");
        
        // Example logic: Assigning a duty
        service.assignDuty(1, "Amrita School Complex", "Monday 9:00 AM");
        
        // Example logic: Routing
        System.out.print("\nEnter subject for script routing: ");
        String sub = scanner.nextLine();
        service.routeScripts(sub);

        System.out.println("\nSystem operation complete.");
        scanner.close();
    }
}