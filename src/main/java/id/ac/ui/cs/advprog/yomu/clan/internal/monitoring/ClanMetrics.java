package id.ac.ui.cs.advprog.yomu.clan.internal.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ClanMetrics {
    public static final String ACTION_COUNTER_METRIC = "yomu_clan_actions_total";
    public static final String ACTION_TIMER_METRIC = "yomu_clan_action_duration";
    public static final String ACTIVITY_EVENT_COUNTER_METRIC = "yomu_clan_activity_events_total";
    public static final String TIER_CHANGE_COUNTER_METRIC = "yomu_clan_tier_changes_total";

    private final MeterRegistry meterRegistry;

    public ClanMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T recordAction(String action, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            outcome = "failure";
            throw exception;
        } finally {
            recordActionMetric(action, outcome, sample);
        }
    }

    public void recordAction(String action, Runnable operation) {
        recordAction(action, () -> {
            operation.run();
            return null;
        });
    }

    public void recordActivityEvent(String event, String outcome) {
        Counter.builder(ACTIVITY_EVENT_COUNTER_METRIC)
            .tag("event", event)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public void recordTierChange(String direction, String source) {
        Counter.builder(TIER_CHANGE_COUNTER_METRIC)
            .tag("direction", direction)
            .tag("source", source)
            .register(meterRegistry)
            .increment();
    }

    private void recordActionMetric(String action, String outcome, Timer.Sample sample) {
        Counter.builder(ACTION_COUNTER_METRIC)
            .tag("action", action)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
        sample.stop(Timer.builder(ACTION_TIMER_METRIC)
            .tag("action", action)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(meterRegistry));
    }
}
