package com.eulerity.taskmanager.repository;

import com.eulerity.taskmanager.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access interface for Task persistence.
 *
 * By extending JpaRepository<Task, Long>, Spring Data JPA automatically
 * generates a full implementation of this interface at startup — including
 * save(), findById(), findAll(), deleteById(), and more. No SQL or
 * implementation code is required here.
 *
 * The two type parameters are:
 *   - Task: the entity class this repository manages
 *   - Long: the type of Task's primary key (@Id field)
 *
 * @Repository marks this as a Spring-managed bean so it can be injected
 * into the service layer via @Autowired or constructor injection. It also
 * enables Spring's exception translation — any low-level JPA exceptions
 * (e.g., ConstraintViolationException) are translated into Spring's
 * consistent DataAccessException hierarchy.
 *
 * Operations available to the service layer (inherited from JpaRepository):
 *   - save(task)          — INSERT if new (id is null), UPDATE if existing
 *   - findById(id)        — SELECT by primary key, returns Optional<Task>
 *   - findAll()           — SELECT all rows, returns List<Task>
 *   - deleteById(id)      — DELETE row by primary key
 *   - existsById(id)      — returns true if a row with that id exists
 *   - count()             — returns total number of rows in the tasks table
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    // No custom query methods needed for basic CRUD.
    // All required operations are inherited from JpaRepository.
}
