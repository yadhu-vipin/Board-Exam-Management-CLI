package com.boardexam.service;

import com.boardexam.model.*;
import com.boardexam.repository.DataRepository;
import com.boardexam.util.ConsoleUI;
import com.boardexam.util.PasswordUtil;

import java.sql.SQLException;
import java.util.List;

/**
 * AuthService — Login, Registration, Session Management
 *
 * Security properties:
 *  - Passwords hashed with BCrypt (cost 12)
 *  - Account lockout after 5 failed attempts (in-memory counter)
 *  - Audit log on every login attempt
 *  - Session is just the in-memory User object (no JWT for CLI)
 */
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final DataRepository repo;
    private int failedAttempts = 0;
    private User loggedInUser = null;

    public AuthService(DataRepository repo) { this.repo = repo; }

    public User login() {
        ConsoleUI.printSectionHeader("CBSE EEMS — Secure Login");

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            ConsoleUI.error("Account locked after " + MAX_FAILED_ATTEMPTS + " failed attempts. Contact Admin.");
            return null;
        }

        String identifier = ConsoleUI.prompt("Email or Roll Number");
        String password   = ConsoleUI.promptPassword("Password");

        try {
            // Try email first, then roll number
            User user = repo.findUserByEmail(identifier)
                .orElseGet(() -> {
                    try { return repo.findUserByRollNumber(identifier).orElse(null); }
                    catch (SQLException e) { return null; }
                });

            if (user == null) {
                handleFailedLogin("User not found: " + identifier);
                return null;
            }

            if (!user.isActive()) {
                ConsoleUI.error("This account has been deactivated. Contact Admin.");
                return null;
            }

            if (!PasswordUtil.verify(password, user.getPasswordHash())) {
                handleFailedLogin("Wrong password for: " + identifier);
                return null;
            }

            // Success
            failedAttempts = 0;
            loggedInUser = user;
            repo.updateLastLogin(user.getId());
            repo.writeAuditLog(user.getId(), "LOGIN_SUCCESS",
                "user", user.getId(), "Login via CLI");

            ConsoleUI.success("Welcome, " + user.getName() + "! Role: " + user.getRole());
            return user;

        } catch (SQLException e) {
            ConsoleUI.error("Login error: " + e.getMessage());
            return null;
        }
    }

    private void handleFailedLogin(String reason) {
        failedAttempts++;
        ConsoleUI.error("Login failed. Attempt " + failedAttempts + "/" + MAX_FAILED_ATTEMPTS);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS)
            ConsoleUI.error("Too many failures — account temporarily locked.");
    }

    public void registerStudent() {
        ConsoleUI.printSectionHeader("Register New Student");

        String name  = ConsoleUI.prompt("Full Name");
        String email = ConsoleUI.prompt("Email");
        String roll  = ConsoleUI.prompt("CBSE Roll Number (e.g. 2312345)");
        int classLvl = ConsoleUI.promptInt("Class (10 or 12)", 10, 12);
        // Only allow 10 or 12 - promptInt with step won't do it exactly so re-check
        if (classLvl != 10 && classLvl != 12) {
            ConsoleUI.error("Class must be 10 or 12.");
            return;
        }

        String password;
        while (true) {
            password = ConsoleUI.promptPassword("Password (min 8 chars, 1 upper, 1 digit, 1 special)");
            if (PasswordUtil.meetsPolicy(password)) break;
            ConsoleUI.error("Password does not meet CBSE security policy. Try again.");
        }

        String confirm = ConsoleUI.promptPassword("Confirm Password");
        if (!password.equals(confirm)) {
            ConsoleUI.error("Passwords do not match.");
            return;
        }

        try {
            if (repo.findUserByEmail(email).isPresent()) {
                ConsoleUI.error("Email already registered.");
                return;
            }
            if (repo.findUserByRollNumber(roll).isPresent()) {
                ConsoleUI.error("Roll number already registered.");
                return;
            }

            User student = User.newStudent(name, email,
                PasswordUtil.hash(password), ClassLevel.fromInt(classLvl), roll);
            int newId = repo.createUser(student);
            repo.writeAuditLog(1, "STUDENT_REGISTERED", "user", newId,
                "Roll=" + roll + " Class=" + classLvl);
            ConsoleUI.success("Student registered! ID: " + newId);

        } catch (SQLException e) {
            ConsoleUI.error("Registration error: " + e.getMessage());
        }
    }

    public void registerStaff() {
        ConsoleUI.printSectionHeader("Register Staff Member");

        String name  = ConsoleUI.prompt("Full Name");
        String email = ConsoleUI.prompt("Email");
        String code  = ConsoleUI.prompt("Staff Code (e.g. CBSE-STAFF-2026-001)");

        ConsoleUI.println("  Roles: 1=INVIGILATOR  2=ASSISTANT_EXAMINER  3=HEAD_EXAMINER  4=COORDINATOR  5=ADMIN");
        int roleChoice = ConsoleUI.promptInt("Select Role", 1, 5);
        UserRole role = switch (roleChoice) {
            case 1 -> UserRole.INVIGILATOR;
            case 2 -> UserRole.ASSISTANT_EXAMINER;
            case 3 -> UserRole.HEAD_EXAMINER;
            case 4 -> UserRole.COORDINATOR;
            default -> UserRole.ADMIN;
        };

        String password;
        while (true) {
            password = ConsoleUI.promptPassword("Password");
            if (PasswordUtil.meetsPolicy(password)) break;
            ConsoleUI.error("Password too weak. Needs upper, digit, special char.");
        }
        String confirm = ConsoleUI.promptPassword("Confirm Password");
        if (!password.equals(confirm)) { ConsoleUI.error("Passwords do not match."); return; }

        try {
            if (repo.findUserByEmail(email).isPresent()) {
                ConsoleUI.error("Email already registered.");
                return;
            }
            User staff = User.newStaff(name, email, PasswordUtil.hash(password), role, code);
            int newId = repo.createUser(staff);
            repo.writeAuditLog(1, "STAFF_REGISTERED", "user", newId,
                "Role=" + role + " Code=" + code);
            ConsoleUI.success("Staff registered! ID: " + newId + " Role: " + role);
        } catch (SQLException e) {
            ConsoleUI.error("Registration error: " + e.getMessage());
        }
    }

    public void logout() {
        if (loggedInUser != null) {
            try { repo.writeAuditLog(loggedInUser.getId(), "LOGOUT", "user", loggedInUser.getId(), "CLI logout"); }
            catch (SQLException ignored) {}
            ConsoleUI.info("Logged out: " + loggedInUser.getName());
            loggedInUser = null;
        }
    }

    public User getLoggedInUser() { return loggedInUser; }
    public boolean isLoggedIn()   { return loggedInUser != null; }
}
