package id.ac.ui.cs.advprog.yomu.clan.internal.service;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.Clan;
import id.ac.ui.cs.advprog.yomu.clan.internal.model.ClanMember;
import id.ac.ui.cs.advprog.yomu.clan.internal.model.Tier;

import java.time.LocalDateTime;
import java.util.UUID;

public final class ClanTestFixtures {

    public static final UUID CLAN_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    public static final UUID LEADER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private ClanTestFixtures() {}

    public static ClanMember member(UUID userId, int personalScore) {
        return member(userId, personalScore, "ACCEPTED");
    }

    public static ClanMember member(UUID userId, int personalScore, String status) {
        return ClanMember.builder()
                .id(UUID.randomUUID())
                .clanId(CLAN_ID)
                .userId(userId)
                .status(status)
                .personalScore(personalScore)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    public static Clan clan(Tier tier, int totalScore) {
        return Clan.builder()
                .id(CLAN_ID)
                .name("Test Clan")
                .description("desc")
                .leaderId(LEADER_ID)
                .tier(tier)
                .totalScore(totalScore)
                .scoreMultiplier(1.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static Clan clanWithId(UUID id, Tier tier, int totalScore, UUID leaderId) {
        return Clan.builder()
                .id(id)
                .name("Clan-" + id.toString().substring(0, 8))
                .description("desc")
                .leaderId(leaderId)
                .tier(tier)
                .totalScore(totalScore)
                .scoreMultiplier(1.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
