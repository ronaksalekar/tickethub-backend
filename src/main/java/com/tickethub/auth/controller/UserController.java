package com.tickethub.auth.controller;

import com.tickethub.auth.model.User;
import com.tickethub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile and account operations")
public class UserController {

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get current user profile", description = "Returns profile information for the authenticated user.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        Map<String, Object> profile = Map.of(
                "id", currentUser.getId(),
                "email", currentUser.getEmail(),
                "fullName", currentUser.getFullName(),
                "role", currentUser.getRole().name()
        );
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched successfully", profile));
    }
}
