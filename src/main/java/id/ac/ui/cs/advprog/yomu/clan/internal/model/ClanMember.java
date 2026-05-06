package id.ac.ui.cs.advprog.yomu.clan.internal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Model ClanMember — pelajar yang tergabung dalam sebuah Clan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanMember {
    private UUID id;
    private UUID clanId;
    private UUID userId;
    private String status; // "PENDING", "ACCEPTED"
    private int personalScore;
    private LocalDateTime joinedAt;
}
