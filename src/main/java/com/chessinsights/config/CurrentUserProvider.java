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
     * Get the current authenticated user. Throws if not found.
     */
    public User getUser(Authentication auth) {
        String supabaseId = auth.getName();
        return userRepository.findBySupabaseId(supabaseId)
                .orElseThrow(() -> new RuntimeException(
                        "User not found. Please complete account setup first."));
    }

    /**
     * Get the current user or null if not authenticated / not set up.
     * Used for endpoints where auth is optional (e.g. viewing public repertoires).
     */
    public User getUserOrNull(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return userRepository.findBySupabaseId(auth.getName()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}