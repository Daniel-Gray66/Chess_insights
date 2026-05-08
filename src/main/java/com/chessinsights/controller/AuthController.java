package com.chessinsights.controller;

import com.chessinsights.client.ChessComClient;
import com.chessinsights.config.JwtUtil;
import com.chessinsights.dto.AuthDto;
import com.chessinsights.entity.User;
import com.chessinsights.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register and login to get a JWT token")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ChessComClient chessComClient;

    @PostMapping("/register")
    @Operation(summary = "Register a new account",
               description = "Creates a new user linked to a Chess.com username. " +
                             "The Chess.com username is validated against their public API.")
    public ResponseEntity<AuthDto.AuthResponse> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        if (userRepository.existsByChessComUsername(request.getChessComUsername())) {
            throw new IllegalArgumentException("Chess.com username already linked to another account");
        }

        // Verify the Chess.com username actually exists
        if (!chessComClient.playerExists(request.getChessComUsername())) {
            throw new IllegalArgumentException(
                    "Chess.com username '" + request.getChessComUsername() + "' not found");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .chessComUsername(request.getChessComUsername().toLowerCase())
                .build();
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                AuthDto.AuthResponse.builder()
                        .token(token)
                        .username(user.getUsername())
                        .chessComUsername(user.getChessComUsername())
                        .build()
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Login to get a JWT token")
    public ResponseEntity<AuthDto.AuthResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(
                AuthDto.AuthResponse.builder()
                        .token(token)
                        .username(user.getUsername())
                        .chessComUsername(user.getChessComUsername())
                        .build()
        );
    }
}