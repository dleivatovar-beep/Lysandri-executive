package com.lysandri.api;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashTest {

    @Test
    void testPasswordHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String adminPass = "admin123";
        String instructorPass = "instructor123";
        String studentPass = "student123";

        String adminHash = encoder.encode(adminPass);
        String instructorHash = encoder.encode(instructorPass);
        String studentHash = encoder.encode(studentPass);

        System.out.println("ADMIN_HASH=" + adminHash);
        System.out.println("INSTRUCTOR_HASH=" + instructorHash);
        System.out.println("STUDENT_HASH=" + studentHash);

        assertTrue(encoder.matches(adminPass, adminHash));
        assertTrue(encoder.matches(instructorPass, instructorHash));
        assertTrue(encoder.matches(studentPass, studentHash));
    }
}
