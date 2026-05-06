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
                score_multiplier DOUBLE NOT NULL DEFAULT 1.0,
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
            CREATE UNIQUE INDEX IF NOT EXISTS idx_clan_member_user
            ON clan_members (user_id)
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

    public void updateMemberStatus(UUID memberId, String status) {
        jdbcTemplate.update(
            "UPDATE clan_members SET status = ? WHERE id = ?",
            status, memberId
        );
    }

    public void updateMemberScore(UUID memberId, int personalScore) {
        jdbcTemplate.update(
            "UPDATE clan_members SET personal_score = ? WHERE id = ?",
            personalScore, memberId
        );
    }

    public void deleteMember(UUID memberId) {
        jdbcTemplate.update("DELETE FROM clan_members WHERE id = ?", memberId);
    }
}
