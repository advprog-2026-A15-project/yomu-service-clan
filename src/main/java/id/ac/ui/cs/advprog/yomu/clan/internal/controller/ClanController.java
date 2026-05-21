package id.ac.ui.cs.advprog.yomu.clan.internal.controller;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.*;
import id.ac.ui.cs.advprog.yomu.clan.internal.controller.dto.CreateClanRequest;
import jakarta.validation.Valid;
import id.ac.ui.cs.advprog.yomu.clan.internal.service.ClanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clan")
@RequiredArgsConstructor
public class ClanController {

    private final ClanService clanService;

    @PostMapping
    public ResponseEntity<Clan> createClan(@Valid @RequestBody CreateClanRequest body, Authentication authentication) {
        String description = body.description() == null ? "" : body.description();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clanService.createClan(
                        body.name(),
                        description,
                        resolveTargetUserId(body.leaderId(), authentication, "User tidak dapat membuat clan untuk akun lain")
                ));
    }

    @GetMapping("/{id}")
    public Clan getClan(@PathVariable UUID id) {
        return clanService.getClanById(id);
    }

    @GetMapping("/leaderboard")
    public List<Clan> leaderboard(@RequestParam(required = false) String tier) {
        return clanService.getLeaderboard(tier);
    }

    @GetMapping("/me")
    public ResponseEntity<ClanMember> getMembership(
            @RequestParam(required = false) UUID userId,
            Authentication authentication) {
        UUID resolvedId = resolveTargetUserId(userId, authentication, "User tidak dapat mengecek membership akun lain");
        return clanService.getMembership(resolvedId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{clanId}/join")
    public ResponseEntity<Void> joinClan(
            @PathVariable UUID clanId,
            @RequestParam(required = false) UUID userId,
            Authentication authentication) {
        clanService.joinClan(
                clanId,
                resolveTargetUserId(userId, authentication, "User tidak dapat mendaftarkan akun lain ke clan")
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{clanId}/members")
    public List<ClanMember> getMembers(@PathVariable UUID clanId) {
        return clanService.getMembers(clanId);
    }

    @GetMapping("/{clanId}/members/pending")
    public List<ClanMember> getPending(@PathVariable UUID clanId) {
        return clanService.getPendingMembers(clanId);
    }

    @PostMapping("/{clanId}/members/{memberId}/accept")
    public ResponseEntity<Void> accept(
            @PathVariable UUID clanId, @PathVariable UUID memberId,
            @RequestParam(required = false) UUID leaderId,
            Authentication authentication) {
        clanService.acceptMember(
                clanId,
                memberId,
                resolveTargetUserId(leaderId, authentication, "User tidak dapat menyetujui member sebagai leader lain")
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{clanId}/members/{memberId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable UUID clanId, @PathVariable UUID memberId,
            @RequestParam(required = false) UUID leaderId,
            Authentication authentication) {
        clanService.rejectMember(
                clanId,
                memberId,
                resolveTargetUserId(leaderId, authentication, "User tidak dapat menolak member sebagai leader lain")
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{clanId}")
    public ResponseEntity<Void> deleteClan(
            @PathVariable UUID clanId,
            @RequestParam(required = false) UUID leaderId,
            Authentication authentication) {
        clanService.deleteClan(
                clanId,
                resolveTargetUserId(leaderId, authentication, "User tidak dapat menghapus clan sebagai leader lain")
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/end-season")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> endSeason() {
        clanService.triggerEndOfSeason();
        return ResponseEntity.ok().build();
    }

    private UUID resolveTargetUserId(UUID requestedUserId, Authentication authentication, String forbiddenMessage) {
        if (authentication == null || authentication.getPrincipal() == null) {
            if (requestedUserId != null) {
                return requestedUserId;
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User tidak terautentikasi");
        }

        UUID authenticatedUserId = UUID.fromString(authentication.getPrincipal().toString());
        if (requestedUserId == null || requestedUserId.equals(authenticatedUserId)) {
            return authenticatedUserId;
        }
        if (isAdmin(authentication)) {
            return requestedUserId;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
