package id.ac.ui.cs.advprog.yomu.clan.internal.service.scoring;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.ClanMember;

import java.util.List;

/**
 * Strategy Pattern: Interface untuk algoritma perhitungan skor berbeda per Tier.
 * Open/Closed Principle: Tier baru = buat class baru yang implement interface ini.
 */
public interface ScoringStrategy {
    int calculateScore(List<ClanMember> members);
}
