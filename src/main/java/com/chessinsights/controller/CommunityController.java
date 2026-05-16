package com.chessinsights.controller;

import com.chessinsights.config.CurrentUserProvider;
import com.chessinsights.dto.RepertoireDtos.*;
import com.chessinsights.entity.Repertoire;
import com.chessinsights.entity.User;
import com.chessinsights.service.RepertoireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
@Tag(name = "Community", description = "Browse, search, bookmark, and copy public repertoires")
public class CommunityController {

    private final RepertoireService repertoireService;
    private final CurrentUserProvider currentUserProvider;

    // ══════════════════════════════════════════════════════════
    //  BROWSE & SEARCH
    // ══════════════════════════════════════════════════════════

    @GetMapping("/repertoires")
    @Operation(summary = "Browse public repertoires",
               description = "Returns all public repertoires, optionally filtered by color.")
    public ResponseEntity<List<CommunityRepertoireResponse>> browse(
            @RequestParam(required = false) Repertoire.Color color) {
        return ResponseEntity.ok(repertoireService.browsePublic(color));
    }

    @GetMapping("/repertoires/search")
    @Operation(summary = "Search public repertoires",
               description = "Search public repertoires by name or description.")
    public ResponseEntity<List<CommunityRepertoireResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) Repertoire.Color color) {
        return ResponseEntity.ok(repertoireService.searchPublic(q, color));
    }

    @GetMapping("/repertoires/{id}")
    @Operation(summary = "View a public repertoire",
               description = "Returns full detail of a public repertoire including lines and moves.")
    public ResponseEntity<RepertoireDetailResponse> viewRepertoire(
            Authentication auth,
            @PathVariable UUID id) {
        User viewer = (auth != null) ? currentUserProvider.getUserOrNull(auth) : null;
        return ResponseEntity.ok(repertoireService.getPublicRepertoireDetail(id, viewer));
    }

    // ══════════════════════════════════════════════════════════
    //  BOOKMARKS
    // ══════════════════════════════════════════════════════════

    @PostMapping("/repertoires/{id}/bookmark")
    @Operation(summary = "Bookmark a public repertoire")
    public ResponseEntity<Void> bookmark(
            Authentication auth,
            @PathVariable UUID id) {
        User user = currentUserProvider.getUser(auth);
        repertoireService.bookmarkRepertoire(user, id);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/repertoires/{id}/bookmark")
    @Operation(summary = "Remove a bookmark")
    public ResponseEntity<Void> unbookmark(
            Authentication auth,
            @PathVariable UUID id) {
        User user = currentUserProvider.getUser(auth);
        repertoireService.unbookmarkRepertoire(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bookmarks")
    @Operation(summary = "List your bookmarked repertoires")
    public ResponseEntity<List<BookmarkResponse>> getBookmarks(Authentication auth) {
        User user = currentUserProvider.getUser(auth);
        return ResponseEntity.ok(repertoireService.getBookmarks(user));
    }

    // ══════════════════════════════════════════════════════════
    //  COPY
    // ══════════════════════════════════════════════════════════

    @PostMapping("/repertoires/{id}/copy")
    @Operation(summary = "Copy a public repertoire to your account",
               description = "Creates a full duplicate of the repertoire under your account. " +
                             "The copy is set to PRIVATE by default.")
    public ResponseEntity<RepertoireResponse> copyRepertoire(
            Authentication auth,
            @PathVariable UUID id) {
        User user = currentUserProvider.getUser(auth);
        RepertoireResponse copy = repertoireService.copyRepertoire(user, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(copy);
    }
}