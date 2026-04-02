package com.eulerity.taskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Task Manager application.
 *
 * @SpringBootApplication is a convenience annotation that combines:
 *   - @Configuration      — marks this as a source of bean definitions
 *   - @EnableAutoConfiguration — tells Spring Boot to automatically configure
 *                               beans based on the JARs on the classpath
 *                               (e.g., sets up H2, JPA, and Tomcat for us)
 *   - @ComponentScan      — scans this package and all sub-packages for
 *                           @Component, @Service, @Repository, @Controller, etc.
 *
 * SpringApplication.run() bootstraps the application, starts the embedded
 * Tomcat server, and makes the API available at http://localhost:8080.
 */
@SpringBootApplication
public class TaskManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskManagerApplication.class, args);
    }
}
