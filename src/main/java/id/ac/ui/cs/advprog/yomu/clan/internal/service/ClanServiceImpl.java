package id.ac.ui.cs.advprog.yomu.clan.internal.service;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.*;
import id.ac.ui.cs.advprog.yomu.clan.internal.repository.ClanRepository;
import id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring.ScoringStrategy;
import id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring.ScoringStrategyFactory;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanDemotedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanPromotedEvent;
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

    public ClanServiceImpl(ClanRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public Clan createClan(String name, String description, UUID leaderId) {
        if (repository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Nama clan sudah digunakan");
        }
        // Cek apakah user sudah ada di clan lain
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

        // Otomatis masukkan leader sebagai member ACCEPTED
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
    }

    @Override
    public Clan getClanById(UUID id) {
        return repository.findClanById(id)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Clan tidak ditemukan"));
    }

    @Override
    public List<Clan> getLeaderboard(String tier) {
        if (tier != null && !tier.isBlank()) {
            try {
                Tier t = Tier.valueOf(tier.toUpperCase());
                List<Clan> clans = repository.findClansByTier(t);
                // Recalculate scores using strategy pattern
                ScoringStrategy strategy = ScoringStrategyFactory.getStrategy(t);
                for (Clan clan : clans) {
                    List<ClanMember> members = repository.findMembersByClanId(clan.getId());
                    int calculatedScore = strategy.calculateScore(members);
                    clan.setTotalScore((int) (calculatedScore * clan.getScoreMultiplier()));
                }
                clans.sort((a, b) -> b.getTotalScore() - a.getTotalScore());
                return clans;
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tier tidak valid: " + tier);
            }
        }
        return repository.findAllClans();
    }

    @Override
    @Transactional
    public void joinClan(UUID clanId, UUID userId) {
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
    }

    @Override
    @Transactional
    public void acceptMember(UUID clanId, UUID memberId, UUID leaderId) {
        Clan clan = getClanById(clanId);
        validateLeader(clan, leaderId);
        repository.updateMemberStatus(memberId, "ACCEPTED");
    }

    @Override
    @Transactional
    public void rejectMember(UUID clanId, UUID memberId, UUID leaderId) {
        Clan clan = getClanById(clanId);
        validateLeader(clan, leaderId);
        repository.deleteMember(memberId);
    }

    @Override
    @Transactional
    public void deleteClan(UUID clanId, UUID leaderId) {
        Clan clan = getClanById(clanId);
        validateLeader(clan, leaderId);
        repository.deleteClanById(clanId);
    }

    @Override
    public List<ClanMember> getMembers(UUID clanId) {
        return repository.findMembersByClanId(clanId);
    }

    @Override
    public List<ClanMember> getPendingMembers(UUID clanId) {
        return repository.findPendingMembersByClanId(clanId);
    }

    @Override
    public Optional<ClanMember> getMembership(UUID userId) {
        return repository.findMemberByUserId(userId);
    }

    @Override
    @Transactional
    public void triggerEndOfSeason() {
        UUID seasonId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        // Untuk setiap tier, proses promosi/degradasi
        for (Tier tier : Tier.values()) {
            List<Clan> clans = repository.findClansByTier(tier);
            if (clans.size() < 2) continue;

            clans.sort((a, b) -> b.getTotalScore() - a.getTotalScore());

            int promoteCount = Math.max(1, clans.size() / 4); // Top 25% naik
            int demoteCount = Math.max(1, clans.size() / 4);  // Bottom 25% turun

            for (int i = 0; i < clans.size(); i++) {
                Clan clan = clans.get(i);
                repository.archiveSeasonResult(seasonId, clan, i + 1);
                if (i < promoteCount && tier.ordinal() < Tier.DIAMOND.ordinal()) {
                    // Promosi
                    Tier nextTier = Tier.values()[tier.ordinal() + 1];
                    repository.updateClanTier(clan.getId(), nextTier);
                    publishClanPromoted(seasonId, clan, tier, nextTier, occurredAt);
                } else if (i >= clans.size() - demoteCount && tier.ordinal() > Tier.BRONZE.ordinal()) {
                    // Degradasi
                    Tier prevTier = Tier.values()[tier.ordinal() - 1];
                    repository.updateClanTier(clan.getId(), prevTier);
                    publishClanDemoted(seasonId, clan, tier, prevTier, occurredAt);
                }
            }
        }

        // Reset semua skor setelah end of season
        for (Clan clan : repository.findAllClans()) {
            repository.updateClanScore(clan.getId(), 0, 1.0);
        }
    }

    @Override
    @Transactional
    public void processUserActivity(UUID userId, int quizScore, java.time.Instant occurredAt) {
        repository.findMemberByUserId(userId).ifPresent(member -> {
            // Update personal score
            int newPersonalScore = member.getPersonalScore() + quizScore;
            repository.updateMemberScore(member.getId(), newPersonalScore);

            // Record activity for buffs/debuffs
            // Assume each quiz has 5 questions for simplicity, or we could pass total questions in event
            repository.recordQuizActivity(userId, member.getClanId(), quizScore, 5);

            // Recalculate clan score and buffs
            updateClanStatus(member.getClanId());
        });
    }

    @Override
    @Transactional
    public void processAchievementUnlocked(UUID userId, String achievementName) {
        repository.findMemberByUserId(userId).ifPresent(member -> {
            // Special bonus for achievements if needed
            int bonus = 50; 
            repository.updateMemberScore(member.getId(), member.getPersonalScore() + bonus);
            updateClanStatus(member.getClanId());
        });
    }

    @Override
    @Transactional
    public void processMissionCompleted(UUID userId) {
        repository.findMemberByUserId(userId).ifPresent(member -> {
            repository.recordMissionCompletion(userId, member.getClanId());
            updateClanStatus(member.getClanId());
        });
    }

    @Override
    @Transactional
    public void addAdminScore(UUID clanId, int score) {
        Clan clan = getClanById(clanId);
        int newScore = clan.getTotalScore() + score;
        clan.setTotalScore(newScore);
        repository.updateClanScore(clanId, newScore, clan.getScoreMultiplier());
    }

    private void updateClanStatus(UUID clanId) {
        Clan clan = getClanById(clanId);
        List<ClanMember> members = repository.findMembersByClanId(clanId);
        
        // 1. Calculate Base Score based on Tier Strategy
        ScoringStrategy strategy = ScoringStrategyFactory.getStrategy(clan.getTier());
        int baseScore = strategy.calculateScore(members);

        // 2. Calculate Multipliers (Buffs/Debuffs)
        ClanRepository.ClanActivitySummary summary = repository.getClanActivitySummary(clanId);
        double multiplier = 1.0;

        // Buff: 50% members complete daily mission
        if (members.size() > 0 && (double) summary.completedMissions() / members.size() >= 0.5) {
            multiplier *= 1.2; // Productivity Buff
        }

        // Debuff: Average accuracy < 50%
        if (summary.totalQuestions() > 0 && (double) summary.totalCorrect() / summary.totalQuestions() < 0.5) {
            multiplier *= 0.8; // Low Accuracy Penalty
        }

        // 3. Update Clan
        repository.updateClanScore(clanId, (int)(baseScore * multiplier), multiplier);
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
