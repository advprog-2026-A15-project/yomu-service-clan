package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.service.ClanTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SilverScoringStrategyTest {

    private final SilverScoringStrategy strategy = new SilverScoringStrategy();

    @Test
    void calculateScore_emptyMembers_returnsZero() {
        assertThat(strategy.calculateScore(List.of())).isZero();
    }

    @Test
    void calculateScore_twoMembers_returnsIntegerAverage() {
        var members = List.of(
                ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10),
                ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, 20)
        );

        assertThat(strategy.calculateScore(members)).isEqualTo(15);
    }
}
