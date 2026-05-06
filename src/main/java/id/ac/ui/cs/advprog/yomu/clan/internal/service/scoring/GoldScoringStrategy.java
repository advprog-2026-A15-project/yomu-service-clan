package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.ClanMember;
import java.util.Comparator;
import java.util.List;

/** Gold Tier: Rata-rata tertimbang (top performer berbobot lebih). */
public class GoldScoringStrategy implements ScoringStrategy {
    @Override
    public int calculateScore(List<ClanMember> members) {
        if (members.isEmpty()) return 0;
        List<ClanMember> sorted = members.stream()
            .sorted(Comparator.comparingInt(ClanMember::getPersonalScore).reversed()).toList();
        double weighted = 0;
        double totalWeight = 0;
        for (int i = 0; i < sorted.size(); i++) {
            double w = sorted.size() - i;
            weighted += sorted.get(i).getPersonalScore() * w;
            totalWeight += w;
        }
        return (int) (weighted / totalWeight);
    }
}
