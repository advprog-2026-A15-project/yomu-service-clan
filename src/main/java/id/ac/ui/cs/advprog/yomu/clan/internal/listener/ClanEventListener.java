package id.ac.ui.cs.advprog.yomu.clan.internal.listener;

import id.ac.ui.cs.advprog.yomu.clan.internal.service.ClanService;
import id.ac.ui.cs.advprog.yomu.shared.event.AchievementUnlockedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClanEventListener {

    private final ClanService clanService;

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "clan.quiz.completed.queue", durable = "true"),
        exchange = @Exchange(value = "yomu.events", type = "topic"),
        key = "yomu.quiz.completed"
    ))
    public void onQuizCompleted(QuizCompletedEvent event) {
        clanService.processUserActivity(event.userId(), event.score(), event.occurredAt());
    }

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "clan.achievement.unlocked.queue", durable = "true"),
        exchange = @Exchange(value = "yomu.events", type = "topic"),
        key = "yomu.achievement.unlocked"
    ))
    public void onAchievementUnlocked(AchievementUnlockedEvent event) {
        clanService.processAchievementUnlocked(event.userId(), event.achievementName());
    }

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "clan.daily.mission.completed.queue", durable = "true"),
        exchange = @Exchange(value = "yomu.events", type = "topic"),
        key = "yomu.daily.mission.completed"
    ))
    public void onMissionCompleted(id.ac.ui.cs.advprog.yomu.shared.event.DailyMissionCompletedEvent event) {
        clanService.processMissionCompleted(event.userId());
    }
}
