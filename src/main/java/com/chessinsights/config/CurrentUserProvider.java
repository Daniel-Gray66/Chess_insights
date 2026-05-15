package com.chessinsights.config;

import com.chessinsights.entity.User;
import com.chessinsights.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    /**
     * Get the current authenticated user from the Supabase JWT.
     * The Authentication principal contains the Supabase user ID (from the "sub" claim).
     */
    public User getUser(Authentication auth) {
        String supabaseId = auth.getName();
        return userRepository.findBySupabaseId(supabaseId)
                .orElseThrow(() -> new RuntimeException(
                        "User not found. Please complete account setup first."));
    }
}