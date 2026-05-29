package id.ac.ui.cs.advprog.yomu.clan.internal.service;

import id.ac.ui.cs.advprog.yomu.clan.internal.model.Clan;
import id.ac.ui.cs.advprog.yomu.clan.internal.model.ClanMember;
import id.ac.ui.cs.advprog.yomu.clan.internal.model.Tier;
import id.ac.ui.cs.advprog.yomu.clan.internal.monitoring.ClanMetrics;
import id.ac.ui.cs.advprog.yomu.clan.internal.repository.ClanRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanDemotedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanPromotedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClanServiceImplTest {

    @Mock
    private ClanRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ClanServiceImpl service;

    private CapturingRabbit captures;

    @BeforeEach
    void setUp() {
        service = new ClanServiceImpl(
            repository,
            rabbitTemplate,
            new ClanMetrics(new SimpleMeterRegistry())
        );
        captures = new CapturingRabbit(rabbitTemplate);
    }

    // --- B1 CRUD & membership ---

    @Test
    void createClan_success_savesClanAndLeaderMember() {
        when(repository.existsByName("New Clan")).thenReturn(false);
        when(repository.findMemberByUserId(ClanTestFixtures.LEADER_ID)).thenReturn(Optional.empty());

        Clan result = service.createClan("New Clan", "desc", ClanTestFixtures.LEADER_ID);

        assertThat(result.getName()).isEqualTo("New Clan");
        assertThat(result.getTier()).isEqualTo(Tier.BRONZE);
        assertThat(result.getLeaderId()).isEqualTo(ClanTestFixtures.LEADER_ID);
        verify(repository).saveClan(any(Clan.class));
        verify(repository).saveMember(any(ClanMember.class));
    }

    @Test
    void createClan_duplicateName_throws409() {
        when(repository.existsByName("Taken")).thenReturn(true);

        assertThatThrownBy(() -> service.createClan("Taken", "d", ClanTestFixtures.LEADER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createClan_leaderAlreadyInClan_throws409() {
        when(repository.existsByName("New")).thenReturn(false);
        when(repository.findMemberByUserId(ClanTestFixtures.LEADER_ID))
                .thenReturn(Optional.of(ClanTestFixtures.member(ClanTestFixtures.LEADER_ID, 0)));

        assertThatThrownBy(() -> service.createClan("New", "d", ClanTestFixtures.LEADER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void getClanById_notFound_throws404() {
        when(repository.findClanById(ClanTestFixtures.CLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getClanById(ClanTestFixtures.CLAN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getClanById_found_returnsClan() {
        Clan clan = ClanTestFixtures.clan(Tier.BRONZE, 0);
        when(repository.findClanById(ClanTestFixtures.CLAN_ID)).thenReturn(Optional.of(clan));

        Clan result = service.getClanById(ClanTestFixtures.CLAN_ID);

        assertThat(result).isSameAs(clan);
    }

    @Test
    void joinClan_clanNotFound_throws404() {
        when(repository.findClanById(ClanTestFixtures.CLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinClan(ClanTestFixtures.CLAN_ID, ClanTestFixtures.MEMBER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void joinClan_userAlreadyMember_throws409() {
        when(repository.findClanById(ClanTestFixtures.CLAN_ID))
                .thenReturn(Optional.of(ClanTestFixtures.clan(Tier.BRONZE, 0)));
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID))
                .thenReturn(Optional.of(ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 0)));

        assertThatThrownBy(() -> service.joinClan(ClanTestFixtures.CLAN_ID, ClanTestFixtures.MEMBER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void joinClan_success_savesPendingMember() {
        when(repository.findClanById(ClanTestFixtures.CLAN_ID))
                .thenReturn(Optional.of(ClanTestFixtures.clan(Tier.BRONZE, 0)));
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.empty());

        service.joinClan(ClanTestFixtures.CLAN_ID, ClanTestFixtures.MEMBER_ID);

        ArgumentCaptor<ClanMember> captor = ArgumentCaptor.forClass(ClanMember.class);
        verify(repository).saveMember(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void acceptMember_nonLeader_throws403() {
        stubClanFound();

        assertThatThrownBy(() -> service.acceptMember(
                ClanTestFixtures.CLAN_ID, UUID.randomUUID(), ClanTestFixtures.OTHER_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void acceptMember_leader_success() {
        stubClanFound();
        UUID memberId = UUID.randomUUID();

        service.acceptMember(ClanTestFixtures.CLAN_ID, memberId, ClanTestFixtures.LEADER_ID);

        verify(repository).updateMemberStatus(ClanTestFixtures.CLAN_ID, memberId, "ACCEPTED");
    }

    @Test
    void rejectMember_leader_deletesMember() {
        stubClanFound();
        UUID memberId = UUID.randomUUID();

        service.rejectMember(ClanTestFixtures.CLAN_ID, memberId, ClanTestFixtures.LEADER_ID);

        verify(repository).deleteMember(ClanTestFixtures.CLAN_ID, memberId);
    }

    @Test
    void deleteClan_leader_success() {
        stubClanFound();

        service.deleteClan(ClanTestFixtures.CLAN_ID, ClanTestFixtures.LEADER_ID);

        verify(repository).deleteClanById(ClanTestFixtures.CLAN_ID);
    }

    @Test
    void leaveClan_notMember_throws404() {
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveClan(ClanTestFixtures.MEMBER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void leaveClan_success_deletesByUserId() {
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID))
                .thenReturn(Optional.of(ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 0)));

        service.leaveClan(ClanTestFixtures.MEMBER_ID);

        verify(repository).deleteMemberByUserId(ClanTestFixtures.MEMBER_ID);
    }

    // --- B2 Leaderboard ---

    @Test
    void getLeaderboard_invalidTier_throws400() {
        assertThatThrownBy(() -> service.getLeaderboard("PLATINUM"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getLeaderboard_withTier_recalculatesAndSortsDesc() {
        UUID clanA = UUID.randomUUID();
        UUID clanB = UUID.randomUUID();
        Clan low = ClanTestFixtures.clanWithId(clanA, Tier.BRONZE, 0, ClanTestFixtures.LEADER_ID);
        Clan high = ClanTestFixtures.clanWithId(clanB, Tier.BRONZE, 0, ClanTestFixtures.OTHER_USER_ID);

        when(repository.findClansByTier(Tier.BRONZE)).thenReturn(new ArrayList<>(List.of(low, high)));
        when(repository.findMembersByClanId(clanA))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.LEADER_ID, 10)));
        when(repository.findMembersByClanId(clanB))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, 30)));

        List<Clan> result = service.getLeaderboard("bronze");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTotalScore()).isGreaterThan(result.get(1).getTotalScore());
        assertThat(result.get(0).getId()).isEqualTo(clanB);
    }

    @Test
    void getLeaderboard_noTier_recalculatesAllClans() {
        Clan c = ClanTestFixtures.clan(Tier.SILVER, 0);
        when(repository.findAllClans()).thenReturn(new ArrayList<>(List.of(c)));
        when(repository.findMembersByClanId(ClanTestFixtures.CLAN_ID))
                .thenReturn(List.of(
                        ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10),
                        ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, 20)));

        List<Clan> result = service.getLeaderboard(null);

        assertThat(result.getFirst().getTotalScore()).isEqualTo(15);
    }

    @Test
    void getLeaderboard_noTier_sortsMultipleClansDescending() {
        UUID lowId = UUID.randomUUID();
        UUID highId = UUID.randomUUID();
        Clan low = ClanTestFixtures.clanWithId(lowId, Tier.BRONZE, 0, ClanTestFixtures.LEADER_ID);
        Clan high = ClanTestFixtures.clanWithId(highId, Tier.BRONZE, 0, ClanTestFixtures.OTHER_USER_ID);
        when(repository.findAllClans()).thenReturn(new ArrayList<>(List.of(low, high)));
        when(repository.findMembersByClanId(lowId))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.LEADER_ID, 5)));
        when(repository.findMembersByClanId(highId))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, 50)));

        List<Clan> result = service.getLeaderboard(null);

        assertThat(result).extracting(Clan::getId).containsExactly(highId, lowId);
    }

    @Test
    void readMembershipViews_delegateToRepository() {
        ClanMember member = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10);
        when(repository.findMembersByClanId(ClanTestFixtures.CLAN_ID)).thenReturn(List.of(member));
        when(repository.findPendingMembersByClanId(ClanTestFixtures.CLAN_ID)).thenReturn(List.of(member));
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(member));

        assertThat(service.getMembers(ClanTestFixtures.CLAN_ID)).containsExactly(member);
        assertThat(service.getPendingMembers(ClanTestFixtures.CLAN_ID)).containsExactly(member);
        assertThat(service.getMembership(ClanTestFixtures.MEMBER_ID)).contains(member);
    }

    // --- B3 Event-driven scoring ---

    @Test
    void processUserActivity_noMembership_noUpdates() {
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.empty());

        service.processUserActivity(ClanTestFixtures.MEMBER_ID, 5, 10, Instant.now());

        verify(repository, never()).updateMemberScore(any(), anyInt());
        verify(repository, never()).recordQuizActivity(any(), any(), anyInt(), anyInt());
        assertThat(captures.sent).isEmpty();
    }

    @Test
    void processUserActivity_pendingMember_noOp() {
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID))
                .thenReturn(Optional.of(ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 0, "PENDING")));

        service.processUserActivity(ClanTestFixtures.MEMBER_ID, 5, 10, Instant.now());

        verify(repository, never()).updateMemberScore(any(), anyInt());
        assertThat(captures.sent).isEmpty();
    }

    @Test
    void processUserActivity_acceptedMember_updatesAndPublishes() {
        ClanMember member = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10);
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(member));
        stubUpdateClanStatusNeutral();

        Instant occurredAt = Instant.parse("2026-05-08T10:00:00Z");
        service.processUserActivity(ClanTestFixtures.MEMBER_ID, 5, 10, occurredAt);

        verify(repository).updateMemberScore(member.getId(), 15);
        verify(repository).recordQuizActivity(
                ClanTestFixtures.MEMBER_ID, ClanTestFixtures.CLAN_ID, 5, 10);
        assertThat(captures.sent).hasSize(1);
        assertThat(captures.sent.getFirst().routingKey()).isEqualTo("yomu.league.activity");
        assertThat(captures.sent.get(0).payload()).isInstanceOf(LeagueActivityEvent.class);
    }

    @Test
    void processAchievementUnlocked_firstTime_addsFiftyPoints() {
        ClanMember member = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10);
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(member));
        when(repository.hasProcessedAchievementBonus(ClanTestFixtures.MEMBER_ID, "BADGE_X")).thenReturn(false);
        stubUpdateClanStatusNeutral();

        service.processAchievementUnlocked(ClanTestFixtures.MEMBER_ID, "BADGE_X");

        verify(repository).markAchievementBonusProcessed(ClanTestFixtures.MEMBER_ID, "BADGE_X");
        verify(repository).updateMemberScore(member.getId(), 60);
    }

    @Test
    void processAchievementUnlocked_duplicate_skipsBonus() {
        ClanMember member = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10);
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(member));
        when(repository.hasProcessedAchievementBonus(ClanTestFixtures.MEMBER_ID, "BADGE_X")).thenReturn(true);

        service.processAchievementUnlocked(ClanTestFixtures.MEMBER_ID, "BADGE_X");

        verify(repository, never()).updateMemberScore(any(), anyInt());
        verify(repository, never()).markAchievementBonusProcessed(any(), anyString());
    }

    @Test
    void processAchievementUnlocked_noAcceptedMembership_skipsBonus() {
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.empty());

        service.processAchievementUnlocked(ClanTestFixtures.MEMBER_ID, "BADGE_X");

        verify(repository, never()).markAchievementBonusProcessed(any(), anyString());
        verify(repository, never()).updateMemberScore(any(), anyInt());
    }

    @Test
    void processMissionCompleted_acceptedMember_recordsMission() {
        ClanMember member = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 0);
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(member));
        stubUpdateClanStatusNeutral();

        service.processMissionCompleted(ClanTestFixtures.MEMBER_ID);

        verify(repository).recordMissionCompletion(ClanTestFixtures.MEMBER_ID, ClanTestFixtures.CLAN_ID);
        verify(repository).updateClanScore(eq(ClanTestFixtures.CLAN_ID), anyInt(), anyDouble());
    }

    @Test
    void processMissionCompleted_noAcceptedMembership_skipsMission() {
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID))
                .thenReturn(Optional.of(ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 0, "PENDING")));

        service.processMissionCompleted(ClanTestFixtures.MEMBER_ID);

        verify(repository, never()).recordMissionCompletion(any(), any());
    }

    @Test
    void processMissionRewardClaimed_acceptedMember_addsRewardAndUpdatesClan() {
        ClanMember member = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10);
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(member));
        stubUpdateClanStatusNeutral();

        service.processMissionRewardClaimed(ClanTestFixtures.MEMBER_ID, 25);

        verify(repository).updateMemberScore(member.getId(), 35);
        verify(repository).updateClanScore(eq(ClanTestFixtures.CLAN_ID), anyInt(), anyDouble());
    }

    @Test
    void processMissionRewardClaimed_noAcceptedMembership_skipsReward() {
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.empty());

        service.processMissionRewardClaimed(ClanTestFixtures.MEMBER_ID, 25);

        verify(repository, never()).updateMemberScore(any(), anyInt());
    }

    @Test
    void processUserActivity_crossesThreshold_promotesClanAndPublishesEvents() {
        ClanMember before = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 90);
        ClanMember after = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 110);
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(before));
        when(repository.findClanById(ClanTestFixtures.CLAN_ID))
                .thenReturn(Optional.of(ClanTestFixtures.clan(Tier.BRONZE, 0)));
        when(repository.findMembersByClanId(ClanTestFixtures.CLAN_ID)).thenReturn(List.of(after));
        when(repository.getClanActivitySummary(ClanTestFixtures.CLAN_ID))
                .thenReturn(new ClanRepository.ClanActivitySummary(0, 0, 10, 20));

        service.processUserActivity(ClanTestFixtures.MEMBER_ID, 20, 20, Instant.now());

        verify(repository).updateClanTier(ClanTestFixtures.CLAN_ID, Tier.SILVER);
        assertThat(captures.sent.stream().anyMatch(s -> s.payload() instanceof ClanPromotedEvent)).isTrue();
    }

    @Test
    void processMissionCompleted_lowerRawScore_demotesClanAndPublishesEvents() {
        ClanMember member = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 50);
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(member));
        when(repository.findClanById(ClanTestFixtures.CLAN_ID))
                .thenReturn(Optional.of(ClanTestFixtures.clan(Tier.SILVER, 0)));
        when(repository.findMembersByClanId(ClanTestFixtures.CLAN_ID)).thenReturn(List.of(member));
        when(repository.getClanActivitySummary(ClanTestFixtures.CLAN_ID))
                .thenReturn(new ClanRepository.ClanActivitySummary(0, 0, 10, 20));

        service.processMissionCompleted(ClanTestFixtures.MEMBER_ID);

        verify(repository).updateClanTier(ClanTestFixtures.CLAN_ID, Tier.BRONZE);
        assertThat(captures.sent.stream().anyMatch(s -> s.payload() instanceof ClanDemotedEvent)).isTrue();
    }

    // --- B4 Buff / debuff ---

    @Test
    void processUserActivity_neutralMultiplier_updatesWithBaseScoreOnly() {
        stubAcceptedMemberForActivity(10, 0);
        when(repository.getClanActivitySummary(ClanTestFixtures.CLAN_ID))
                .thenReturn(new ClanRepository.ClanActivitySummary(0, 0, 10, 20));

        service.processUserActivity(ClanTestFixtures.MEMBER_ID, 0, 5, Instant.now());

        verify(repository).updateClanScore(ClanTestFixtures.CLAN_ID, 10, 1.0);
    }

    @Test
    void processUserActivity_productivityBuff_appliesMultiplier12() {
        stubAcceptedMemberForActivity(10, 10);
        when(repository.getClanActivitySummary(ClanTestFixtures.CLAN_ID))
                .thenReturn(new ClanRepository.ClanActivitySummary(2, 1, 10, 20));

        service.processUserActivity(ClanTestFixtures.MEMBER_ID, 0, 5, Instant.now());

        verify(repository).updateClanScore(ClanTestFixtures.CLAN_ID, 24, 1.2);
    }

    @Test
    void processUserActivity_lowAccuracyDebuff_appliesMultiplier08() {
        stubAcceptedMemberForActivity(10, 0);
        when(repository.getClanActivitySummary(ClanTestFixtures.CLAN_ID))
                .thenReturn(new ClanRepository.ClanActivitySummary(0, 0, 2, 10));

        service.processUserActivity(ClanTestFixtures.MEMBER_ID, 0, 5, Instant.now());

        verify(repository).updateClanScore(ClanTestFixtures.CLAN_ID, 8, 0.8);
    }

    @Test
    void processUserActivity_buffAndDebuff_stackMultipliers() {
        stubAcceptedMemberForActivity(10, 10);
        when(repository.getClanActivitySummary(ClanTestFixtures.CLAN_ID))
                .thenReturn(new ClanRepository.ClanActivitySummary(2, 1, 2, 10));

        service.processUserActivity(ClanTestFixtures.MEMBER_ID, 0, 5, Instant.now());

        verify(repository).updateClanScore(ClanTestFixtures.CLAN_ID, 19, 0.96);
    }

    // --- B5 End of season ---

    @Test
    void triggerEndOfSeason_singleClanInTier_skipsPromotion() {
        when(repository.findClansByTier(Tier.BRONZE))
                .thenReturn(new ArrayList<>(List.of(ClanTestFixtures.clan(Tier.BRONZE, 100))));
        when(repository.findClansByTier(Tier.SILVER)).thenReturn(new ArrayList<>());
        when(repository.findClansByTier(Tier.GOLD)).thenReturn(new ArrayList<>());
        when(repository.findClansByTier(Tier.DIAMOND)).thenReturn(new ArrayList<>());
        when(repository.findAllClans()).thenReturn(new ArrayList<>());

        service.triggerEndOfSeason();

        verify(repository, never()).updateClanTier(any(), any());
        verify(repository).resetAllMemberScores();
        verify(repository).clearAllDailyActivity();
    }

    @Test
    void triggerEndOfSeason_fourBronzeClans_promotesTopAndArchives() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        List<Clan> clans = new ArrayList<>(List.of(
                ClanTestFixtures.clanWithId(id1, Tier.BRONZE, 10, ClanTestFixtures.LEADER_ID),
                ClanTestFixtures.clanWithId(id2, Tier.BRONZE, 40, ClanTestFixtures.MEMBER_ID),
                ClanTestFixtures.clanWithId(id3, Tier.BRONZE, 30, ClanTestFixtures.OTHER_USER_ID),
                ClanTestFixtures.clanWithId(id4, Tier.BRONZE, 20, UUID.randomUUID())
        ));

        when(repository.findClansByTier(Tier.BRONZE)).thenReturn(clans);
        when(repository.findClansByTier(Tier.SILVER)).thenReturn(List.of());
        when(repository.findClansByTier(Tier.GOLD)).thenReturn(List.of());
        when(repository.findClansByTier(Tier.DIAMOND)).thenReturn(List.of());
        when(repository.findAllClans()).thenReturn(clans);

        for (Clan c : clans) {
            when(repository.findMembersByClanId(c.getId()))
                    .thenReturn(List.of(ClanTestFixtures.member(c.getLeaderId(), c.getTotalScore())));
        }

        service.triggerEndOfSeason();

        verify(repository).updateClanTier(id2, Tier.SILVER);
        verify(repository, atLeastOnce()).archiveSeasonResult(any(), any(), anyInt());
        verify(repository, times(4)).updateClanScore(any(), eq(0), eq(1.0));
        assertThat(captures.sent.stream().anyMatch(s -> s.routingKey().equals("yomu.clan.promoted"))).isTrue();
        assertThat(captures.sent.stream().anyMatch(s -> s.payload() instanceof ClanPromotedEvent)).isTrue();
    }

    @Test
    void triggerEndOfSeason_resetsAllScoresAndActivity() {
        Clan c = ClanTestFixtures.clan(Tier.BRONZE, 50);
        when(repository.findClansByTier(Tier.BRONZE)).thenReturn(new ArrayList<>(List.of(c, c)));
        when(repository.findClansByTier(Tier.SILVER)).thenReturn(List.of());
        when(repository.findClansByTier(Tier.GOLD)).thenReturn(List.of());
        when(repository.findClansByTier(Tier.DIAMOND)).thenReturn(List.of());
        when(repository.findAllClans()).thenReturn(List.of(c));
        when(repository.findMembersByClanId(ClanTestFixtures.CLAN_ID))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.LEADER_ID, 10)));

        service.triggerEndOfSeason();

        verify(repository).resetAllMemberScores();
        verify(repository).clearAllDailyActivity();
    }

    @Test
    void triggerEndOfSeason_silverTier_demotesBottomClanAndPublishesEvent() {
        UUID highId = UUID.randomUUID();
        UUID lowId = UUID.randomUUID();
        Clan high = ClanTestFixtures.clanWithId(highId, Tier.SILVER, 200, ClanTestFixtures.LEADER_ID);
        Clan low = ClanTestFixtures.clanWithId(lowId, Tier.SILVER, 20, ClanTestFixtures.MEMBER_ID);
        when(repository.findClansByTier(Tier.BRONZE)).thenReturn(List.of());
        when(repository.findClansByTier(Tier.SILVER)).thenReturn(new ArrayList<>(List.of(high, low)));
        when(repository.findClansByTier(Tier.GOLD)).thenReturn(List.of());
        when(repository.findClansByTier(Tier.DIAMOND)).thenReturn(List.of());
        when(repository.findAllClans()).thenReturn(List.of(high, low));
        when(repository.findMembersByClanId(highId))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.LEADER_ID, 200)));
        when(repository.findMembersByClanId(lowId))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 20)));

        service.triggerEndOfSeason();

        verify(repository).updateClanTier(lowId, Tier.BRONZE);
        assertThat(captures.sent.stream().anyMatch(s -> s.payload() instanceof ClanDemotedEvent)).isTrue();
    }

    @Test
    void recalculateAllTiers_updatesEveryClan() {
        Clan clanA = ClanTestFixtures.clanWithId(UUID.randomUUID(), Tier.BRONZE, 0, ClanTestFixtures.LEADER_ID);
        Clan clanB = ClanTestFixtures.clanWithId(UUID.randomUUID(), Tier.SILVER, 0, ClanTestFixtures.MEMBER_ID);
        when(repository.findAllClans()).thenReturn(List.of(clanA, clanB));
        when(repository.findClanById(clanA.getId())).thenReturn(Optional.of(clanA));
        when(repository.findClanById(clanB.getId())).thenReturn(Optional.of(clanB));
        when(repository.findMembersByClanId(clanA.getId()))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.LEADER_ID, 10)));
        when(repository.findMembersByClanId(clanB.getId()))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 200)));
        when(repository.getClanActivitySummary(any()))
                .thenReturn(new ClanRepository.ClanActivitySummary(0, 0, 10, 20));

        service.recalculateAllTiers();

        verify(repository).updateClanScore(eq(clanA.getId()), anyInt(), anyDouble());
        verify(repository).updateClanScore(eq(clanB.getId()), anyInt(), anyDouble());
    }

    @Test
    void addAdminScore_clanFound_updatesScore() {
        Clan clan = ClanTestFixtures.clan(Tier.BRONZE, 25);
        when(repository.findClanById(ClanTestFixtures.CLAN_ID)).thenReturn(Optional.of(clan));

        service.addAdminScore(ClanTestFixtures.CLAN_ID, 15);

        verify(repository).updateClanScore(ClanTestFixtures.CLAN_ID, 40, 1.0);
    }

    @Test
    void addAdminScore_clanNotFound_throws404() {
        when(repository.findClanById(ClanTestFixtures.CLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addAdminScore(ClanTestFixtures.CLAN_ID, 15))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void stubClanFound() {
        when(repository.findClanById(ClanTestFixtures.CLAN_ID))
                .thenReturn(Optional.of(ClanTestFixtures.clan(Tier.BRONZE, 0)));
    }

    private void stubUpdateClanStatusNeutral() {
        when(repository.findClanById(ClanTestFixtures.CLAN_ID))
                .thenReturn(Optional.of(ClanTestFixtures.clan(Tier.BRONZE, 0)));
        when(repository.findMembersByClanId(ClanTestFixtures.CLAN_ID))
                .thenReturn(List.of(ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, 10)));
        when(repository.getClanActivitySummary(ClanTestFixtures.CLAN_ID))
                .thenReturn(new ClanRepository.ClanActivitySummary(0, 0, 10, 20));
    }

    private void stubAcceptedMemberForActivity(int scoreA, int scoreB) {
        ClanMember m1 = ClanTestFixtures.member(ClanTestFixtures.MEMBER_ID, scoreA);
        when(repository.findMemberByUserId(ClanTestFixtures.MEMBER_ID)).thenReturn(Optional.of(m1));
        when(repository.findClanById(ClanTestFixtures.CLAN_ID))
                .thenReturn(Optional.of(ClanTestFixtures.clan(Tier.BRONZE, 0)));
        when(repository.findMembersByClanId(ClanTestFixtures.CLAN_ID))
                .thenReturn(List.of(
                        m1,
                        ClanTestFixtures.member(ClanTestFixtures.OTHER_USER_ID, scoreB)));
    }

    static final class CapturingRabbit {
        final List<SentMessage> sent = new ArrayList<>();

        CapturingRabbit(RabbitTemplate template) {
            org.mockito.Mockito.doAnswer(inv -> {
                sent.add(new SentMessage(
                        inv.getArgument(0, String.class),
                        inv.getArgument(1, Object.class)));
                return null;
            }).when(template).convertAndSend(org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.any(Object.class));
        }
    }

    record SentMessage(String routingKey, Object payload) {}
}
