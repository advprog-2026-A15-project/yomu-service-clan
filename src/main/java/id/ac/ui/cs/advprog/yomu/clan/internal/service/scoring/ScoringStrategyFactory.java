package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.Tier;

/** Factory untuk memilih ScoringStrategy berdasarkan Tier. */
public final class ScoringStrategyFactory {
    private ScoringStrategyFactory() {}

    public static ScoringStrategy getStrategy(Tier tier) {
        return switch (tier) {
            case BRONZE -> new BronzeScoringStrategy();
            case SILVER -> new SilverScoringStrategy();
            case GOLD -> new GoldScoringStrategy();
            case DIAMOND -> new DiamondScoringStrategy();
        };
    }
}
