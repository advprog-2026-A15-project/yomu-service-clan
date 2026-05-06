package id.ac.ui.cs.advprog.yomu.clan.internal.service;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.*;

import java.util.List;
import java.util.UUID;

/**
 * Interface ClanService — Abstraksi untuk business logic modul Clan & Liga.
 */
public interface ClanService {

    Clan createClan(String name, String description, UUID leaderId);

    Clan getClanById(UUID id);

    List<Clan> getLeaderboard(String tier);

    void joinClan(UUID clanId, UUID userId);

    void acceptMember(UUID clanId, UUID memberId, UUID leaderId);

    void rejectMember(UUID clanId, UUID memberId, UUID leaderId);

    void deleteClan(UUID clanId, UUID leaderId);

    List<ClanMember> getMembers(UUID clanId);

    List<ClanMember> getPendingMembers(UUID clanId);

    void triggerEndOfSeason();
}
