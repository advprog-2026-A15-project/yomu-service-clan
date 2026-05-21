package id.ac.ui.cs.advprog.yomu.clan.internal.repository;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.Clan;
import id.ac.ui.cs.advprog.yomu.clan.internal.model.ClanMember;
import id.ac.ui.cs.advprog.yomu.clan.internal.model.Tier;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ClanRepository {

    private final JdbcTemplate jdbcTemplate;

    public ClanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS clans (
                id UUID PRIMARY KEY,
                name VARCHAR(255) UNIQUE NOT NULL,
                description TEXT,
                leader_id UUID NOT NULL,
                tier VARCHAR(50) NOT NULL DEFAULT 'BRONZE',
                total_score INTEGER NOT NULL DEFAULT 0,
                score_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS clan_members (
                id UUID PRIMARY KEY,
                clan_id UUID NOT NULL,
                user_id UUID NOT NULL,
                status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                personal_score INTEGER NOT NULL DEFAULT 0,
                joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS member_activity (
                id UUID PRIMARY KEY,
                clan_id UUID NOT NULL,
                user_id UUID NOT NULL,
                activity_date DATE NOT NULL,
                quizzes_taken INTEGER NOT NULL DEFAULT 0,
                total_correct_answers INTEGER NOT NULL DEFAULT 0,
                total_questions INTEGER NOT NULL DEFAULT 0,
                mission_completed BOOLEAN NOT NULL DEFAULT FALSE,
                UNIQUE(user_id, activity_date)
            )
        """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS season_archives (
                id UUID PRIMARY KEY,
                season_id UUID NOT NULL,
                clan_id UUID NOT NULL,
                clan_name VARCHAR(255) NOT NULL,
                tier VARCHAR(50) NOT NULL,
                rank_position INTEGER NOT NULL,
                total_score INTEGER NOT NULL,
                score_multiplier DOUBLE PRECISION NOT NULL,
                archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS clan_processed_achievement_bonuses (
                user_id UUID NOT NULL,
                achievement_code VARCHAR(100) NOT NULL,
                processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, achievement_code)
            )
        """);
    }

    // ─── Clan ────────────────────────────────────────────────────────

    private final RowMapper<Clan> clanRowMapper = (rs, rowNum) -> Clan.builder()
            .id(rs.getObject("id", UUID.class))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .leaderId(rs.getObject("leader_id", UUID.class))
            .tier(Tier.valueOf(rs.getString("tier")))
            .totalScore(rs.getInt("total_score"))
            .scoreMultiplier(rs.getDouble("score_multiplier"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
            .build();

    public Clan saveClan(Clan clan) {
        if (clan.getId() == null) clan.setId(UUID.randomUUID());
        if (clan.getCreatedAt() == null) clan.setCreatedAt(LocalDateTime.now());
        if (clan.getUpdatedAt() == null) clan.setUpdatedAt(LocalDateTime.now());

        jdbcTemplate.update("""
            INSERT INTO clans (id, name, description, leader_id, tier, total_score, score_multiplier, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            clan.getId(), clan.getName(), clan.getDescription(),
            clan.getLeaderId(), clan.getTier().name(),
            clan.getTotalScore(), clan.getScoreMultiplier(),
            Timestamp.valueOf(clan.getCreatedAt()),
            Timestamp.valueOf(clan.getUpdatedAt())
        );
        return clan;
    }

    public Optional<Clan> findClanById(UUID id) {
        return jdbcTemplate.query("SELECT * FROM clans WHERE id = ?", clanRowMapper, id)
                .stream().findFirst();
    }

    public List<Clan> findAllClans() {
        return jdbcTemplate.query(
            "SELECT * FROM clans ORDER BY total_score DESC",
            clanRowMapper
        );
    }

    public List<Clan> findClansByTier(Tier tier) {
        return jdbcTemplate.query(
            "SELECT * FROM clans WHERE tier = ? ORDER BY total_score DESC",
            clanRowMapper, tier.name()
        );
    }

    public boolean existsByName(String name) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM clans WHERE name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    public void updateClanScore(UUID clanId, int totalScore, double multiplier) {
        jdbcTemplate.update("""
            UPDATE clans SET total_score = ?, score_multiplier = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, totalScore, multiplier, clanId);
    }

    public void updateClanTier(UUID clanId, Tier tier) {
        jdbcTemplate.update("""
            UPDATE clans SET tier = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
            """, tier.name(), clanId);
    }

    public void archiveSeasonResult(UUID seasonId, Clan clan, int rankPosition) {
        jdbcTemplate.update("""
            INSERT INTO season_archives
                (id, season_id, clan_id, clan_name, tier, rank_position, total_score, score_multiplier, archived_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            UUID.randomUUID(),
            seasonId,
            clan.getId(),
            clan.getName(),
            clan.getTier().name(),
            rankPosition,
            clan.getTotalScore(),
            clan.getScoreMultiplier()
        );
    }

    public int deleteClanById(UUID id) {
        jdbcTemplate.update("DELETE FROM clan_members WHERE clan_id = ?", id);
        return jdbcTemplate.update("DELETE FROM clans WHERE id = ?", id);
    }

    // ─── ClanMember ──────────────────────────────────────────────────

    private final RowMapper<ClanMember> memberRowMapper = (rs, rowNum) -> ClanMember.builder()
            .id(rs.getObject("id", UUID.class))
            .clanId(rs.getObject("clan_id", UUID.class))
            .userId(rs.getObject("user_id", UUID.class))
            .status(rs.getString("status"))
            .personalScore(rs.getInt("personal_score"))
            .joinedAt(rs.getTimestamp("joined_at").toLocalDateTime())
            .build();

    public ClanMember saveMember(ClanMember member) {
        if (member.getId() == null) member.setId(UUID.randomUUID());
        if (member.getJoinedAt() == null) member.setJoinedAt(LocalDateTime.now());

        jdbcTemplate.update("""
            INSERT INTO clan_members (id, clan_id, user_id, status, personal_score, joined_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            member.getId(), member.getClanId(), member.getUserId(),
            member.getStatus(), member.getPersonalScore(),
            Timestamp.valueOf(member.getJoinedAt())
        );
        return member;
    }

    public List<ClanMember> findMembersByClanId(UUID clanId) {
        return jdbcTemplate.query(
            "SELECT * FROM clan_members WHERE clan_id = ? AND status = 'ACCEPTED' ORDER BY personal_score DESC",
            memberRowMapper, clanId
        );
    }

    public List<ClanMember> findPendingMembersByClanId(UUID clanId) {
        return jdbcTemplate.query(
            "SELECT * FROM clan_members WHERE clan_id = ? AND status = 'PENDING'",
            memberRowMapper, clanId
        );
    }

    public Optional<ClanMember> findMemberByUserId(UUID userId) {
        return jdbcTemplate.query(
            "SELECT * FROM clan_members WHERE user_id = ?",
            memberRowMapper, userId
        ).stream().findFirst();
    }

    public void updateMemberStatus(UUID clanId, UUID memberId, String status) {
        int updated = jdbcTemplate.update(
            "UPDATE clan_members SET status = ? WHERE id = ? AND clan_id = ?",
            status, memberId, clanId
        );
        if (updated == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Member tidak ditemukan di clan ini");
        }
    }

    public void updateMemberScore(UUID memberId, int personalScore) {
        jdbcTemplate.update(
            "UPDATE clan_members SET personal_score = ? WHERE id = ?",
            personalScore, memberId
        );
    }

    public void deleteMember(UUID clanId, UUID memberId) {
        int deleted = jdbcTemplate.update(
            "DELETE FROM clan_members WHERE id = ? AND clan_id = ?",
            memberId, clanId
        );
        if (deleted == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Member tidak ditemukan di clan ini");
        }
    }

    public void deleteMemberByUserId(UUID userId) {
        jdbcTemplate.update("DELETE FROM clan_members WHERE user_id = ?", userId);
    }

    public void resetAllMemberScores() {
        jdbcTemplate.update("UPDATE clan_members SET personal_score = 0");
    }

    public void clearAllDailyActivity() {
        jdbcTemplate.update("DELETE FROM member_activity");
    }

    public boolean hasProcessedAchievementBonus(UUID userId, String achievementCode) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM clan_processed_achievement_bonuses WHERE user_id = ? AND achievement_code = ?",
            Integer.class, userId, achievementCode);
        return count != null && count > 0;
    }

    public void markAchievementBonusProcessed(UUID userId, String achievementCode) {
        jdbcTemplate.update(
            "INSERT INTO clan_processed_achievement_bonuses (user_id, achievement_code) VALUES (?, ?) ON CONFLICT DO NOTHING",
            userId, achievementCode);
    }

    public void recordQuizActivity(UUID userId, UUID clanId, int correct, int total) {
        jdbcTemplate.update("""
            INSERT INTO member_activity (id, clan_id, user_id, activity_date, quizzes_taken, total_correct_answers, total_questions)
            VALUES (?, ?, ?, CURRENT_DATE, 1, ?, ?)
            ON CONFLICT (user_id, activity_date) DO UPDATE SET
                quizzes_taken = member_activity.quizzes_taken + 1,
                total_correct_answers = member_activity.total_correct_answers + EXCLUDED.total_correct_answers,
                total_questions = member_activity.total_questions + EXCLUDED.total_questions
            """, UUID.randomUUID(), clanId, userId, correct, total);
    }

    public void recordMissionCompletion(UUID userId, UUID clanId) {
        jdbcTemplate.update("""
            INSERT INTO member_activity (id, clan_id, user_id, activity_date, mission_completed)
            VALUES (?, ?, ?, CURRENT_DATE, TRUE)
            ON CONFLICT (user_id, activity_date) DO UPDATE SET
                mission_completed = TRUE
            """, UUID.randomUUID(), clanId, userId);
    }

    public ClanActivitySummary getClanActivitySummary(UUID clanId) {
        return jdbcTemplate.queryForObject("""
            SELECT 
                COUNT(*) as total_members,
                SUM(CASE WHEN mission_completed THEN 1 ELSE 0 END) as completed_missions,
                SUM(total_correct_answers) as total_correct,
                SUM(total_questions) as total_q
            FROM member_activity 
            WHERE clan_id = ? AND activity_date = CURRENT_DATE
            """, (rs, rowNum) -> new ClanActivitySummary(
                rs.getInt("total_members"),
                rs.getInt("completed_missions"),
                rs.getInt("total_correct"),
                rs.getInt("total_q")
            ), clanId);
    }

    public record ClanActivitySummary(int activeMembers, int completedMissions, int totalCorrect, int totalQuestions) {}
}
