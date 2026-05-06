package id.ac.ui.cs.advprog.yomu.clan.internal.controller;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.*;
import id.ac.ui.cs.advprog.yomu.clan.internal.service.ClanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/clan")
@RequiredArgsConstructor
public class ClanController {

    private final ClanService clanService;

    @PostMapping
    public ResponseEntity<Clan> createClan(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.getOrDefault("description", "");
        UUID leaderId = UUID.fromString(body.get("leaderId"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clanService.createClan(name, description, leaderId));
    }

    @GetMapping("/{id}")
    public Clan getClan(@PathVariable UUID id) {
        return clanService.getClanById(id);
    }

    @GetMapping("/leaderboard")
    public List<Clan> leaderboard(@RequestParam(required = false) String tier) {
        return clanService.getLeaderboard(tier);
    }

    @PostMapping("/{clanId}/join")
    public ResponseEntity<Void> joinClan(@PathVariable UUID clanId, @RequestParam UUID userId) {
        clanService.joinClan(clanId, userId);
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
            @RequestParam UUID leaderId) {
        clanService.acceptMember(clanId, memberId, leaderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{clanId}/members/{memberId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable UUID clanId, @PathVariable UUID memberId,
            @RequestParam UUID leaderId) {
        clanService.rejectMember(clanId, memberId, leaderId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{clanId}")
    public ResponseEntity<Void> deleteClan(
            @PathVariable UUID clanId, @RequestParam UUID leaderId) {
        clanService.deleteClan(clanId, leaderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/end-season")
    public ResponseEntity<Void> endSeason() {
        clanService.triggerEndOfSeason();
        return ResponseEntity.ok().build();
    }
}
