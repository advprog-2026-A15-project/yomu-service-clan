package id.ac.ui.cs.advprog.yomu.clan.internal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Model Clan.
 * Pelajar yang membuat Clan akan menjadi Ketua Clan (leaderId).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clan {
    private UUID id;
    private String name;
    private String description;
    private UUID leaderId;       // Ketua Clan
    private Tier tier;           // Divisi saat ini
    private int totalScore;      // Skor kumulatif
    private double scoreMultiplier; // Buff/Debuff multiplier
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
