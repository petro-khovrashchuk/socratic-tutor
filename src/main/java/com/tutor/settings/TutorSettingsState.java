package com.tutor.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "TutorSettingsState", storages = @Storage("tutor.settings.xml"))
public final class TutorSettingsState implements PersistentStateComponent<TutorSettingsState.State> {

    public static final int DAILY_SOFT_SWITCH_THRESHOLD_PERCENT = 95;

    public static @NotNull TutorSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(TutorSettingsState.class);
    }

    public static final class State {
        public long dailyQuotaUsed = 0L;
        public long monthlyQuotaUsed = 0L;
        public boolean autoNavigationEnabled = true;
    }

    private State state = new State();

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public void recordTokenUsage(long tokens) {
        state.dailyQuotaUsed += tokens;
        state.monthlyQuotaUsed += tokens;
    }

    public void resetDailyUsage() {
        state.dailyQuotaUsed = 0L;
    }

    public void resetMonthlyUsage() {
        state.monthlyQuotaUsed = 0L;
    }

    public boolean isAutoNavigationEnabled() {
        return state.autoNavigationEnabled;
    }

    public void setAutoNavigationEnabled(boolean autoNavigationEnabled) {
        state.autoNavigationEnabled = autoNavigationEnabled;
    }

    public long getDailyQuotaUsed() {
        return state.dailyQuotaUsed;
    }

    public long getMonthlyQuotaUsed() {
        return state.monthlyQuotaUsed;
    }

    public double percentageOfDaily(long dailyLimit) {
        if (dailyLimit <= 0) {
            return 0d;
        }
        return Math.min(100d, (state.dailyQuotaUsed * 100d) / dailyLimit);
    }

    public boolean shouldSoftSwitch(long dailyLimit) {
        double usage = percentageOfDaily(dailyLimit);
        return usage >= DAILY_SOFT_SWITCH_THRESHOLD_PERCENT;
    }
}
