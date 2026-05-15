package com.chessinsights.repository;

import com.chessinsights.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findBySupabaseId(String supabaseId);
    boolean existsByUsername(String username);
    boolean existsByChessComUsername(String chessComUsername);
    boolean existsBySupabaseId(String supabaseId);
}