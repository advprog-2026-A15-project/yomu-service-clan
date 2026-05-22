package id.ac.ui.cs.advprog.yomu.clan.internal.service;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.*;
import id.ac.ui.cs.advprog.yomu.clan.internal.monitoring.ClanMetrics;
import id.ac.ui.cs.advprog.yomu.clan.internal.repository.ClanRepository;
import id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring.ScoringStrategy;
import id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring.ScoringStrategyFactory;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanDemotedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanPromotedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementasi ClanService menggunakan Strategy Pattern untuk scoring per tier.
 * Bronze: penjumlahan total, Silver: rata-rata, Gold: rata-rata tertimbang,
 * Diamond: rata-rata tertimbang dengan penalti.
 */
@Service
public class ClanServiceImpl implements ClanService {

    private final ClanRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ClanMetrics metrics;

    public ClanServiceImpl(
            ClanRepository repository,
            RabbitTemplate rabbitTemplate,
            ClanMetrics metrics) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public Clan createClan(String name, String description, UUID leaderId) {
        return metrics.recordAction("create_clan", () -> {
            if (repository.existsByName(name)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nama clan sudah digunakan");
            }
            if (repository.findMemberByUserId(leaderId).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Anda sudah tergabung dalam clan lain");
            }

            Clan clan = Clan.builder()
                    .id(UUID.randomUUID())
                    .name(name)
                    .description(description)
                    .leaderId(leaderId)
                    .tier(Tier.BRONZE)
                    .totalScore(0)
                    .scoreMultiplier(1.0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            repository.saveClan(clan);

            ClanMember leader = ClanMember.builder()
                    .id(UUID.randomUUID())
                    .clanId(clan.getId())
                    .userId(leaderId)
                    .status("ACCEPTED")
                    .personalScore(0)
                    .joinedAt(LocalDateTime.now())
                    .build();
            repository.saveMember(leader);

            return clan;
        });
    }

    @Override
    public Clan getClanById(UUID id) {
        return metrics.recordAction("get_clan_by_id", () -> findClanOrThrow(id));
    }

    private Clan findClanOrThrow(UUID id) {
        return repository.findClanById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Clan tidak ditemukan"));
    }

    @Override
    public List<Clan> getLeaderboard(String tier) {
        return metrics.recordAction("get_leaderboard", () -> {
            if (tier != null && !tier.isBlank()) {
                try {
                    Tier t = Tier.valueOf(tier.toUpperCase());
                    return recalculateAndSortClans(repository.findClansByTier(t), t);
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tier tidak valid: " + tier);
                }
            }
            List<Clan> all = repository.findAllClans();
            for (Clan clan : all) {
                ScoringStrategy strategy = ScoringStrategyFactory.getStrategy(clan.getTier());
                List<ClanMember> members = repository.findMembersByClanId(clan.getId());
                clan.setTotalScore((int) (strategy.calculateScore(members) * clan.getScoreMultiplier()));
            }
            all.sort((a, b) -> b.getTotalScore() - a.getTotalScore());
            return all;
        });
    }

    private List<Clan> recalculateAndSortClans(List<Clan> clans, Tier tier) {
        ScoringStrategy strategy = ScoringStrategyFactory.getStrategy(tier);
        for (Clan clan : clans) {
            List<ClanMember> members = repository.findMembersByClanId(clan.getId());
            clan.setTotalScore((int) (strategy.calculateScore(members) * clan.getScoreMultiplier()));
        }
        clans.sort((a, b) -> b.getTotalScore() - a.getTotalScore());
        return clans;
    }

    @Override
    @Transactional
    public void joinClan(UUID clanId, UUID userId) {
        metrics.recordAction("join_clan", () -> {
            repository.findClanById(clanId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Clan tidak ditemukan"));

            if (repository.findMemberByUserId(userId).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Anda sudah tergabung dalam clan lain");
            }

            ClanMember member = ClanMember.builder()
                    .id(UUID.randomUUID())
                    .clanId(clanId)
                    .userId(userId)
                    .status("PENDING")
                    .personalScore(0)
                    .joinedAt(LocalDateTime.now())
                    .build();

            repository.saveMember(member);
        });
    }

    @Override
    @Transactional
    public void acceptMember(UUID clanId, UUID memberId, UUID leaderId) {
        metrics.recordAction("accept_member", () -> {
            Clan clan = findClanOrThrow(clanId);
            validateLeader(clan, leaderId);
            repository.updateMemberStatus(clanId, memberId, "ACCEPTED");
        });
    }

    @Override
    @Transactional
    public void rejectMember(UUID clanId, UUID memberId, UUID leaderId) {
        metrics.recordAction("reject_member", () -> {
            Clan clan = findClanOrThrow(clanId);
            validateLeader(clan, leaderId);
            repository.deleteMember(clanId, memberId);
        });
    }

    @Override
    @Transactional
    public void deleteClan(UUID clanId, UUID leaderId) {
        metrics.recordAction("delete_clan", () -> {
            Clan clan = findClanOrThrow(clanId);
            validateLeader(clan, leaderId);
            repository.deleteClanById(clanId);
        });
    }

    @Override
    public List<ClanMember> getMembers(UUID clanId) {
        return metrics.recordAction("get_members", () -> repository.findMembersByClanId(clanId));
    }

    @Override
    public List<ClanMember> getPendingMembers(UUID clanId) {
        return metrics.recordAction("get_pending_members", () -> repository.findPendingMembersByClanId(clanId));
    }

    @Override
    public Optional<ClanMember> getMembership(UUID userId) {
        return metrics.recordAction("get_membership", () -> repository.findMemberByUserId(userId));
    }

    @Override
    @Transactional
    public void triggerEndOfSeason() {
        metrics.recordAction("trigger_end_of_season", () -> {
            UUID seasonId = UUID.randomUUID();
            Instant occurredAt = Instant.now();

            for (Tier tier : Tier.values()) {
                List<Clan> clans = repository.findClansByTier(tier);
                if (clans.size() < 2) continue;

                recalculateAndSortClans(clans, tier);

                int promoteCount = Math.max(1, clans.size() / 4);
                int demoteCount = Math.max(1, clans.size() / 4);

                for (int i = 0; i < clans.size(); i++) {
                    Clan clan = clans.get(i);
                    repository.archiveSeasonResult(seasonId, clan, i + 1);
                    if (i < promoteCount && tier.ordinal() < Tier.DIAMOND.ordinal()) {
                        Tier nextTier = Tier.values()[tier.ordinal() + 1];
                        repository.updateClanTier(clan.getId(), nextTier);
                        publishClanPromoted(seasonId, clan, tier, nextTier, occurredAt);
                    } else if (i >= clans.size() - demoteCount && tier.ordinal() > Tier.BRONZE.ordinal()) {
                        Tier prevTier = Tier.values()[tier.ordinal() - 1];
                        repository.updateClanTier(clan.getId(), prevTier);
                        publishClanDemoted(seasonId, clan, tier, prevTier, occurredAt);
                    }
                }
            }

            for (Clan clan : repository.findAllClans()) {
                repository.updateClanScore(clan.getId(), 0, 1.0);
            }
            repository.resetAllMemberScores();
            repository.clearAllDailyActivity();
        });
    }

    @Override
    @Transactional
    public void leaveClan(UUID userId) {
        metrics.recordAction("leave_clan", () -> {
            repository.findMemberByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Anda tidak tergabung dalam clan manapun"));
            repository.deleteMemberByUserId(userId);
        });
    }

    @Override
    @Transactional
    public void processUserActivity(UUID userId, int quizScore, int totalQuestions, Instant occurredAt) {
        metrics.recordAction("process_user_activity", () ->
            repository.findMemberByUserId(userId)
                .filter(m -> "ACCEPTED".equals(m.getStatus()))
                .ifPresentOrElse(member -> {
                    int newPersonalScore = member.getPersonalScore() + quizScore;
                    repository.updateMemberScore(member.getId(), newPersonalScore);
                    repository.recordQuizActivity(userId, member.getClanId(), quizScore, totalQuestions);
                    rabbitTemplate.convertAndSend("yomu.league.activity", new LeagueActivityEvent(
                        userId, member.getClanId(), UUID.randomUUID(), "QUIZ_COMPLETED", occurredAt
                    ));
                    updateClanStatus(member.getClanId());
                    metrics.recordActivityEvent("quiz_completed", "processed");
                }, () -> metrics.recordActivityEvent("quiz_completed", "skipped_no_member")));
    }

    @Override
    @Transactional
    public void processAchievementUnlocked(UUID userId, String achievementCode) {
        metrics.recordAction("process_achievement_unlocked", () ->
            repository.findMemberByUserId(userId)
                .filter(m -> "ACCEPTED".equals(m.getStatus()))
                .ifPresentOrElse(member -> {
                    if (repository.hasProcessedAchievementBonus(userId, achievementCode)) {
                        metrics.recordActivityEvent("achievement_unlocked", "duplicate");
                        return;
                    }
                    repository.markAchievementBonusProcessed(userId, achievementCode);
                    repository.updateMemberScore(member.getId(), member.getPersonalScore() + 50);
                    updateClanStatus(member.getClanId());
                    metrics.recordActivityEvent("achievement_unlocked", "processed");
                }, () -> metrics.recordActivityEvent("achievement_unlocked", "skipped_no_member")));
    }

    @Override
    @Transactional
    public void processMissionCompleted(UUID userId) {
        metrics.recordAction("process_mission_completed", () ->
            repository.findMemberByUserId(userId)
                .filter(m -> "ACCEPTED".equals(m.getStatus()))
                .ifPresentOrElse(member -> {
                    repository.recordMissionCompletion(userId, member.getClanId());
                    updateClanStatus(member.getClanId());
                    metrics.recordActivityEvent("mission_completed", "processed");
                }, () -> metrics.recordActivityEvent("mission_completed", "skipped_no_member")));
    }

    @Override
    @Transactional
    public void processMissionRewardClaimed(UUID userId, int rewardPoints) {
        metrics.recordAction("process_mission_reward_claimed", () ->
            repository.findMemberByUserId(userId)
                .filter(m -> "ACCEPTED".equals(m.getStatus()))
                .ifPresentOrElse(member -> {
                    int newScore = member.getPersonalScore() + rewardPoints;
                    repository.updateMemberScore(member.getId(), newScore);
                    updateClanStatus(member.getClanId());
                    metrics.recordActivityEvent("mission_reward_claimed", "processed");
                }, () -> metrics.recordActivityEvent("mission_reward_claimed", "skipped_no_member")));
    }

    @Override
    @Transactional
    public void recalculateAllTiers() {
        metrics.recordAction("recalculate_all_tiers", () -> {
            for (Clan clan : repository.findAllClans()) {
                updateClanStatus(clan.getId());
            }
        });
    }

    @Override
    @Transactional
    public void addAdminScore(UUID clanId, int score) {
        metrics.recordAction("add_admin_score", () -> {
            Clan clan = findClanOrThrow(clanId);
            int newScore = clan.getTotalScore() + score;
            clan.setTotalScore(newScore);
            repository.updateClanScore(clanId, newScore, clan.getScoreMultiplier());
        });
    }

    private void updateClanStatus(UUID clanId) {
        Clan clan = findClanOrThrow(clanId);
        List<ClanMember> members = repository.findMembersByClanId(clanId);

        int rawSum = members.stream().mapToInt(ClanMember::getPersonalScore).sum();
        Tier targetTier = Tier.fromScore(rawSum);
        Tier currentTier = clan.getTier();

        if (targetTier != currentTier) {
            repository.updateClanTier(clanId, targetTier);
            clan.setTier(targetTier);
            publishTierChangeEvents(clan, currentTier, targetTier);
        }

        ScoringStrategy strategy = ScoringStrategyFactory.getStrategy(targetTier);
        int baseScore = strategy.calculateScore(members);

        ClanRepository.ClanActivitySummary summary = repository.getClanActivitySummary(clanId);
        double multiplier = 1.0;

        if (!members.isEmpty() && (double) summary.completedMissions() / members.size() >= 0.5) {
            multiplier *= 1.2;
        }
        if (summary.totalQuestions() > 0 && (double) summary.totalCorrect() / summary.totalQuestions() < 0.5) {
            multiplier *= 0.8;
        }

        repository.updateClanScore(clanId, (int)(baseScore * multiplier), multiplier);
    }

    private void publishTierChangeEvents(Clan clan, Tier previousTier, Tier newTier) {
        UUID seasonId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        List<ClanMember> members = repository.findMembersByClanId(clan.getId());

        if (newTier.ordinal() > previousTier.ordinal()) {
            metrics.recordTierChange("promoted", "auto_score");
            for (ClanMember member : members) {
                rabbitTemplate.convertAndSend("yomu.clan.promoted", new ClanPromotedEvent(
                    seasonId, clan.getId(), member.getUserId(),
                    clan.getName(), previousTier.name(), newTier.name(), occurredAt
                ));
            }
        } else {
            metrics.recordTierChange("demoted", "auto_score");
            for (ClanMember member : members) {
                rabbitTemplate.convertAndSend("yomu.clan.demoted", new ClanDemotedEvent(
                    seasonId, clan.getId(), member.getUserId(),
                    clan.getName(), previousTier.name(), newTier.name(), occurredAt
                ));
            }
        }
    }

    private void validateLeader(Clan clan, UUID leaderId) {
        if (!clan.getLeaderId().equals(leaderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Hanya Ketua Clan yang dapat melakukan aksi ini");
        }
    }

    private void publishClanPromoted(
            UUID seasonId,
            Clan clan,
            Tier previousTier,
            Tier newTier,
            Instant occurredAt) {
        metrics.recordTierChange("promoted", "season_end");
        for (ClanMember member : repository.findMembersByClanId(clan.getId())) {
            rabbitTemplate.convertAndSend("yomu.clan.promoted", new ClanPromotedEvent(
                seasonId,
                clan.getId(),
                member.getUserId(),
                clan.getName(),
                previousTier.name(),
                newTier.name(),
                occurredAt
            ));
        }
    }

    private void publishClanDemoted(
            UUID seasonId,
            Clan clan,
            Tier previousTier,
            Tier newTier,
            Instant occurredAt) {
        metrics.recordTierChange("demoted", "season_end");
        for (ClanMember member : repository.findMembersByClanId(clan.getId())) {
            rabbitTemplate.convertAndSend("yomu.clan.demoted", new ClanDemotedEvent(
                seasonId,
                clan.getId(),
                member.getUserId(),
                clan.getName(),
                previousTier.name(),
                newTier.name(),
                occurredAt
            ));
        }
    }
}
