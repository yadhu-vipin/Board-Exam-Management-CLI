package com.boardexam.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a system user — either a Student or a Staff member.
 * 
 * NORMALIZATION: 1NF, 2NF, 3NF compliant.
 * - All attributes depend ONLY on the primary key (id).
 * - No transitive dependencies. ClassLevel info stored as enum, not repeated strings.
 * - passwordHash is NEVER the plaintext password (BCrypt, cost=12).
 */
public class User {
    private int id;
    private String name;
    private String email;
    private String passwordHash;     // BCrypt hash, never plaintext
    private UserRole role;
    private ClassLevel classLevel;   // Only relevant for STUDENTs
    private String rollNumber;       // CBSE Roll number (students only)
    private String staffCode;        // Staff ID code (staff only)
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    // ──────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────

    public User() {}

    /** Full constructor used when loading from DB */
    public User(int id, String name, String email, String passwordHash,
                UserRole role, ClassLevel classLevel, String rollNumber,
                String staffCode, boolean isActive,
                LocalDateTime createdAt, LocalDateTime lastLogin) {
        this.id           = id;
        this.name         = name;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.classLevel   = classLevel;
        this.rollNumber   = rollNumber;
        this.staffCode    = staffCode;
        this.isActive     = isActive;
        this.createdAt    = createdAt;
        this.lastLogin    = lastLogin;
    }

    /** Builder-style factory for new Students */
    public static User newStudent(String name, String email, String passwordHash,
                                   ClassLevel classLevel, String rollNumber) {
        User u = new User();
        u.name         = name;
        u.email        = email;
        u.passwordHash = passwordHash;
        u.role         = UserRole.STUDENT;
        u.classLevel   = classLevel;
        u.rollNumber   = rollNumber;
        u.isActive     = true;
        return u;
    }

    /** Builder-style factory for new Staff */
    public static User newStaff(String name, String email, String passwordHash,
                                 UserRole role, String staffCode) {
        User u = new User();
        u.name         = name;
        u.email        = email;
        u.passwordHash = passwordHash;
        u.role         = role;
        u.staffCode    = staffCode;
        u.isActive     = true;
        return u;
    }

    // ──────────────────────────────────────────────
    // Business Logic Methods (OOP: Behaviour in Model)
    // ──────────────────────────────────────────────

    public boolean isStudent()     { return role == UserRole.STUDENT; }
    public boolean isStaff()       { return role != UserRole.STUDENT; }
    public boolean isClass12()     { return classLevel == ClassLevel.CLASS_12; }
    public boolean isClass10()     { return classLevel == ClassLevel.CLASS_10; }
    public boolean canMarkScripts(){ return role == UserRole.ASSISTANT_EXAMINER
                                         || role == UserRole.HEAD_EXAMINER
                                         || role == UserRole.COORDINATOR; }

    public String getDisplayIdentifier() {
        return isStudent() ? "Roll: " + rollNumber : "Staff: " + staffCode;
    }

    // ──────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────

    public int getId()                        { return id; }
    public void setId(int id)                 { this.id = id; }
    public String getName()                   { return name; }
    public void setName(String name)          { this.name = name; }
    public String getEmail()                  { return email; }
    public void setEmail(String email)        { this.email = email; }
    public String getPasswordHash()           { return passwordHash; }
    public void setPasswordHash(String h)     { this.passwordHash = h; }
    public UserRole getRole()                 { return role; }
    public void setRole(UserRole role)        { this.role = role; }
    public ClassLevel getClassLevel()         { return classLevel; }
    public void setClassLevel(ClassLevel cl)  { this.classLevel = cl; }
    public String getRollNumber()             { return rollNumber; }
    public void setRollNumber(String r)       { this.rollNumber = r; }
    public String getStaffCode()              { return staffCode; }
    public void setStaffCode(String s)        { this.staffCode = s; }
    public boolean isActive()                 { return isActive; }
    public void setActive(boolean active)     { this.isActive = active; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getLastLogin()       { return lastLogin; }
    public void setLastLogin(LocalDateTime t) { this.lastLogin = t; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;
        return id == u.id;
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "User{id=%d, name='%s', role=%s, %s}"
                .formatted(id, name, role, getDisplayIdentifier());
    }
}
