package com.boardexam.model;

/**
 * CBSE class levels with their associated evaluation modes.
 * Class 10: Physical/Manual marking (Section-wise compliance)
 * Class 12: On-Screen Marking (OSM) with anonymization
 */
public enum ClassLevel {
    CLASS_10(10, "PHYSICAL", "Section-Wise Manual Marking"),
    CLASS_12(12, "OSM",      "On-Screen Marking with Anonymization");

    private final int level;
    private final String gradingType;
    private final String description;

    ClassLevel(int level, String gradingType, String description) {
        this.level = level;
        this.gradingType = gradingType;
        this.description = description;
    }

    public int getLevel()            { return level; }
    public String getGradingType()   { return gradingType; }
    public String getDescription()   { return description; }

    public static ClassLevel fromInt(int level) {
        for (ClassLevel cl : values()) {
            if (cl.level == level) return cl;
        }
        throw new IllegalArgumentException("Invalid class level: " + level);
    }
}
