package com.chessinsights.controller;

import com.chessinsights.client.ChessComClient;
import com.chessinsights.entity.User;
import com.chessinsights.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User management after Supabase OAuth login")
public class AuthController {

    private final UserRepository userRepository;
    private final ChessComClient chessComClient;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserResponse> getMe(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            log.warn("/me called without authentication");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(UserResponse.builder().needsSetup(true).build());
        }

        String supabaseId = auth.getName();
        log.info("/me called for supabaseId: {}", supabaseId);

        User user = userRepository.findBySupabaseId(supabaseId).orElse(null);

        if (user == null) {
            return ResponseEntity.ok(UserResponse.builder()
                    .supabaseId(supabaseId)
                    .needsSetup(true)
                    .build());
        }

        return ResponseEntity.ok(UserResponse.builder()
                .supabaseId(user.getSupabaseId())
                .username(user.getUsername())
                .chessComUsername(user.getChessComUsername())
                .email(user.getEmail())
                .needsSetup(false)
                .build());
    }

    @PostMapping("/setup")
    @Operation(summary = "Complete account setup")
    public ResponseEntity<UserResponse> setup(
            Authentication auth,
            @Valid @RequestBody SetupRequest request) {

        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String supabaseId = auth.getName();
        log.info("/setup called for supabaseId: {}", supabaseId);

        if (userRepository.existsBySupabaseId(supabaseId)) {
            throw new IllegalArgumentException("Account already set up");
        }

        if (userRepository.existsByChessComUsername(request.getChessComUsername())) {
            throw new IllegalArgumentException("Chess.com username already linked to another account");
        }

        if (!chessComClient.playerExists(request.getChessComUsername())) {
            throw new IllegalArgumentException(
                    "Chess.com username '" + request.getChessComUsername() + "' not found");
        }

        if (request.getDisplayName() != null && userRepository.existsByUsername(request.getDisplayName())) {
            throw new IllegalArgumentException("Display name already taken");
        }

        User user = User.builder()
                .supabaseId(supabaseId)
                .username(request.getDisplayName() != null ? request.getDisplayName() : request.getChessComUsername())
                .chessComUsername(request.getChessComUsername().toLowerCase())
                .email(request.getEmail())
                .build();

        userRepository.save(user);
        log.info("New user created: {} (Chess.com: {})", user.getUsername(), user.getChessComUsername());

        return ResponseEntity.ok(UserResponse.builder()
                .supabaseId(user.getSupabaseId())
                .username(user.getUsername())
                .chessComUsername(user.getChessComUsername())
                .email(user.getEmail())
                .needsSetup(false)
                .build());
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SetupRequest {
        @NotBlank(message = "Chess.com username is required")
        private String chessComUsername;
        private String displayName;
        private String email;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserResponse {
        private String supabaseId;
        private String username;
        private String chessComUsername;
        private String email;
        private boolean needsSetup;
    }
}