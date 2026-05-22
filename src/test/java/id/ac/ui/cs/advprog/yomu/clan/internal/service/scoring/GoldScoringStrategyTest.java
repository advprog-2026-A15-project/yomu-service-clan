package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.service.ClanTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldScoringStrategyTest {

    private final GoldScoringStrategy strategy = new GoldScoringStrategy();

    @Test
    void calculateScore_emptyMembers_returnsZero() {
        assertThat(strategy.calculateScore(List.of())).isZero();
    }

    @Test
    void calculateScore_twoMembers_returnsWeightedAverage() {
        // sorted [20, 10]: (20*2 + 10*1) / 3 = 50/3 = 16
        var members = List.of(
                ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10),
                ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, 20)
        );

        assertThat(strategy.calculateScore(members)).isEqualTo(16);
    }

    @Test
    void calculateScore_threeMembers_higherRankWeightedMore() {
        // sorted [30, 10, 5]: (30*3 + 10*2 + 5*1) / 6 = 115/6 = 19
        var members = List.of(
                ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 5),
                ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, 30),
                ClanTestFixtures.member(ClanTestFixtures.LEADER_ID, 10)
        );

        assertThat(strategy.calculateScore(members)).isEqualTo(19);
    }
}
