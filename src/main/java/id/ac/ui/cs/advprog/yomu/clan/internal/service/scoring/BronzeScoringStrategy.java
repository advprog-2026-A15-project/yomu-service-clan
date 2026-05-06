package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.ClanMember;

import java.util.List;

/**
 * Bronze Tier: Penjumlahan total skor anggota.
 */
public class BronzeScoringStrategy implements ScoringStrategy {
    @Override
    public int calculateScore(List<ClanMember> members) {
        return members.stream()
                .mapToInt(ClanMember::getPersonalScore)
                .sum();
    }
}
