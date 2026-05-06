package id.ac.ui.cs.advprog.yomu.clan.internal.listener;

import id.ac.ui.cs.advprog.yomu.clan.internal.service.ClanService;
import id.ac.ui.cs.advprog.yomu.shared.event.AchievementUnlockedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClanEventListener {

    private final ClanService clanService;

    @RabbitListener(queuesToDeclare = @Queue("yomu.quiz.completed.clan"))
    public void onQuizCompleted(QuizCompletedEvent event) {
        clanService.processUserActivity(event.userId(), event.score(), event.occurredAt());
    }

    @RabbitListener(queuesToDeclare = @Queue("yomu.achievement.unlocked.clan"))
    public void onAchievementUnlocked(AchievementUnlockedEvent event) {
        clanService.processAchievementUnlocked(event.userId(), event.achievementName());
    }

    @RabbitListener(queuesToDeclare = @Queue("yomu.daily.mission.completed.clan"))
    public void onMissionCompleted(id.ac.ui.cs.advprog.yomu.shared.event.DailyMissionCompletedEvent event) {
        clanService.processMissionCompleted(event.userId());
    }
}
