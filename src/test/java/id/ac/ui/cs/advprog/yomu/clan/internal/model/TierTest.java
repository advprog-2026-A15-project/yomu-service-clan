package id.ac.ui.cs.advprog.yomu.clan.internal.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TierTest {

    @Test
    void fromScore_mapsThresholdBoundaries() {
        assertThat(Tier.fromScore(0)).isEqualTo(Tier.BRONZE);
        assertThat(Tier.fromScore(99)).isEqualTo(Tier.BRONZE);
        assertThat(Tier.fromScore(100)).isEqualTo(Tier.SILVER);
        assertThat(Tier.fromScore(299)).isEqualTo(Tier.SILVER);
        assertThat(Tier.fromScore(300)).isEqualTo(Tier.GOLD);
        assertThat(Tier.fromScore(599)).isEqualTo(Tier.GOLD);
        assertThat(Tier.fromScore(600)).isEqualTo(Tier.DIAMOND);
    }

    @Test
    void minScore_returnsTierLowerBound() {
        assertThat(Tier.BRONZE.minScore()).isZero();
        assertThat(Tier.SILVER.minScore()).isEqualTo(Tier.SILVER_THRESHOLD);
        assertThat(Tier.GOLD.minScore()).isEqualTo(Tier.GOLD_THRESHOLD);
        assertThat(Tier.DIAMOND.minScore()).isEqualTo(Tier.DIAMOND_THRESHOLD);
    }

    @Test
    void maxScoreLabel_returnsReadableRange() {
        assertThat(Tier.BRONZE.maxScoreLabel()).isEqualTo("< " + Tier.SILVER_THRESHOLD);
        assertThat(Tier.SILVER.maxScoreLabel()).contains("100", "299");
        assertThat(Tier.GOLD.maxScoreLabel()).contains("300", "599");
        assertThat(Tier.DIAMOND.maxScoreLabel()).isEqualTo("600+");
    }
}
