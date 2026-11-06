package com.tickethub.auth;

import com.tickethub.auth.model.Role;
import com.tickethub.auth.model.User;
import com.tickethub.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // 256-bit base64-encoded secret key
        ReflectionTestUtils.setField(jwtService, "secretKeyHex", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L); // 1 hour

        testUser = User.builder()
                .id(1L)
                .email("john.doe@example.com")
                .password("encoded_pass")
                .fullName("John Doe")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("Should generate a valid JWT token and correctly extract subject")
    void shouldGenerateAndExtractUsername() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token);
        assertFalse(token.isBlank());

        String username = jwtService.extractUsername(token);
        assertEquals("john.doe@example.com", username);
        assertTrue(jwtService.isTokenValid(token, testUser));
    }

    @Test
    @DisplayName("Token should be invalid for a different user")
    void shouldBeInvalidForDifferentUser() {
        String token = jwtService.generateToken(testUser);

        User anotherUser = User.builder()
                .email("another@example.com")
                .build();

        assertFalse(jwtService.isTokenValid(token, anotherUser));
    }
}
