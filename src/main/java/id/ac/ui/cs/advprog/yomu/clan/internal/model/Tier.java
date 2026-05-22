package id.ac.ui.cs.advprog.yomu.clan.internal.model;

/**
 * Tier/Divisi dalam sistem Liga.
 * Setiap Tier memiliki algoritma perhitungan skor yang berbeda (Strategy Pattern).
 *
 * Threshold promosi otomatis berdasarkan raw sum personalScore seluruh anggota:
 *   BRONZE  :   0  –  99
 *   SILVER  : 100  – 299
 *   GOLD    : 300  – 599
 *   DIAMOND : 600+
 */
public enum Tier {
    BRONZE,
    SILVER,
    GOLD,
    DIAMOND;

    public static final int SILVER_THRESHOLD  = 100;
    public static final int GOLD_THRESHOLD    = 300;
    public static final int DIAMOND_THRESHOLD = 600;

    /** Tentukan tier berdasarkan total raw sum personalScore anggota clan. */
    public static Tier fromScore(int rawSum) {
        if (rawSum >= DIAMOND_THRESHOLD) return DIAMOND;
        if (rawSum >= GOLD_THRESHOLD)    return GOLD;
        if (rawSum >= SILVER_THRESHOLD)  return SILVER;
        return BRONZE;
    }

    public int minScore() {
        if (this == DIAMOND) return DIAMOND_THRESHOLD;
        if (this == GOLD)    return GOLD_THRESHOLD;
        if (this == SILVER)  return SILVER_THRESHOLD;
        return 0;
    }

    public String maxScoreLabel() {
        if (this == BRONZE)  return "< " + SILVER_THRESHOLD;
        if (this == SILVER)  return SILVER_THRESHOLD + " – " + (GOLD_THRESHOLD - 1);
        if (this == GOLD)    return GOLD_THRESHOLD + " – " + (DIAMOND_THRESHOLD - 1);
        return DIAMOND_THRESHOLD + "+";
    }
}
