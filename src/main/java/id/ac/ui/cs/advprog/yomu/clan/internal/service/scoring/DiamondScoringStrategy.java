package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.ClanMember;
import java.util.Comparator;
import java.util.List;

/** Diamond Tier: Rata-rata tertimbang dengan penalti anggota inactive. */
public class DiamondScoringStrategy implements ScoringStrategy {
    @Override
    public int calculateScore(List<ClanMember> members) {
        if (members.isEmpty()) return 0;
        List<ClanMember> sorted = members.stream()
            .sorted(Comparator.comparingInt(ClanMember::getPersonalScore).reversed()).toList();
        double weighted = 0;
        double totalWeight = 0;
        int inactiveCount = 0;
        for (int i = 0; i < sorted.size(); i++) {
            double w = sorted.size() - i;
            if (sorted.get(i).getPersonalScore() == 0) inactiveCount++;
            weighted += sorted.get(i).getPersonalScore() * w;
            totalWeight += w;
        }
        double base = weighted / totalWeight;
        double penalty = 1.0 - (inactiveCount * 0.1);
        return (int) (base * Math.max(penalty, 0.5));
    }
}
