package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringStrategyFactoryTest {

    @Test
    void getStrategy_bronze_returnsBronzeStrategy() {
        assertThat(ScoringStrategyFactory.getStrategy(Tier.BRONZE))
                .isInstanceOf(BronzeScoringStrategy.class);
    }

    @Test
    void getStrategy_silver_returnsSilverStrategy() {
        assertThat(ScoringStrategyFactory.getStrategy(Tier.SILVER))
                .isInstanceOf(SilverScoringStrategy.class);
    }

    @Test
    void getStrategy_gold_returnsGoldStrategy() {
        assertThat(ScoringStrategyFactory.getStrategy(Tier.GOLD))
                .isInstanceOf(GoldScoringStrategy.class);
    }

    @Test
    void getStrategy_diamond_returnsDiamondStrategy() {
        assertThat(ScoringStrategyFactory.getStrategy(Tier.DIAMOND))
                .isInstanceOf(DiamondScoringStrategy.class);
    }
}
