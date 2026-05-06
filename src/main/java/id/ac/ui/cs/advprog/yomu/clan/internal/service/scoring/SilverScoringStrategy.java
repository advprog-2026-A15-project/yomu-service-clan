package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.ClanMember;
import java.util.List;

/** Silver Tier: Rata-rata skor anggota. */
public class SilverScoringStrategy implements ScoringStrategy {
    @Override
    public int calculateScore(List<ClanMember> members) {
        if (members.isEmpty()) return 0;
        return members.stream().mapToInt(ClanMember::getPersonalScore).sum() / members.size();
    }
}
