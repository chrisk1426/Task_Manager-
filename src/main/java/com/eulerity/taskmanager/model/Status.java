package com.eulerity.taskmanager.model;

/**
 * Represents the current state of a task in its lifecycle.
 *
 * Using an enum instead of a plain String locks down the set of valid states
 * to exactly three values: TODO, IN_PROGRESS, and DONE. This makes state
 * transitions self-documenting and prevents invalid states (e.g., "PENDING"
 * or "FINISHED") from entering the system.
 *
 * Like Priority, JPA stores this as a string column via
 * @Enumerated(EnumType.STRING) on the Task entity, keeping database rows
 * readable and resilient to enum reordering.
 */
public enum Status {
    TODO,
    IN_PROGRESS,
    DONE
}
