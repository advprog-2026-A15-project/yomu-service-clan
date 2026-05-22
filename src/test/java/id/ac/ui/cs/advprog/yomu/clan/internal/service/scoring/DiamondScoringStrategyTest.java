package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.service.ClanTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiamondScoringStrategyTest {

    private final DiamondScoringStrategy strategy = new DiamondScoringStrategy();
    private final GoldScoringStrategy goldStrategy = new GoldScoringStrategy();

    @Test
    void calculateScore_emptyMembers_returnsZero() {
        assertThat(strategy.calculateScore(List.of())).isZero();
    }

    @Test
    void calculateScore_allActive_matchesGoldWeightedAverage() {
        var members = List.of(
                ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10),
                ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, 20)
        );

        assertThat(strategy.calculateScore(members)).isEqualTo(goldStrategy.calculateScore(members));
    }

    @Test
    void calculateScore_oneInactive_appliesPenalty() {
        UUID inactiveUser = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var members = List.of(
                ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, 20),
                ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10),
                ClanTestFixtures.member(inactiveUser, 0)
        );

        int goldScore = goldStrategy.calculateScore(members);
        int diamondScore = strategy.calculateScore(members);

        assertThat(diamondScore).isLessThan(goldScore);
        // base 13, penalty 0.9 -> 12
        assertThat(diamondScore).isEqualTo(12);
    }
}
