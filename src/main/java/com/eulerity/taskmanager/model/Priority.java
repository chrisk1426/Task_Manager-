package com.eulerity.taskmanager.model;

/**
 * Represents the priority level of a task.
 *
 * Using an enum instead of a plain String ensures that only valid priority
 * values (LOW, MEDIUM, HIGH) can exist in the system. The compiler rejects
 * any invalid value at compile time rather than allowing bad data to reach
 * the database.
 *
 * JPA stores this as a string column (e.g., "HIGH") rather than an ordinal
 * integer (e.g., 2). This is controlled by @Enumerated(EnumType.STRING) on
 * the Task entity — string storage makes the database rows human-readable
 * and safe to reorder without breaking existing data.
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH
}
