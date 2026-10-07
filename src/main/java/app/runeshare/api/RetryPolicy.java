package app.runeshare.api;

import lombok.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value
public class RetryPolicy {
    /**
     * A newer tab change replaces a pending retry, so these only matter while the
     * player leaves the tab alone, and are spaced out so an outage isn't hammered.
     */
    public static final RetryPolicy BANK_TAB = new RetryPolicy(List.of(30_000L, 60_000L, 120_000L), true);

    /**
     * The player has already moved on from the session locally, so give up
     * quickly. A 429 isn't retried because waiting 5s wouldn't clear it.
     */
    public static final RetryPolicy STOP_TASK_SESSION = new RetryPolicy(List.of(5_000L, 5_000L), false);

    /**
     * How long to wait before each retry, so its size is the number of retries.
     */
    List<Long> delaysMs;

    boolean retryTooManyRequests;

    /**
     * @param failedAttempts attempts made so far, all of which failed, including the one that produced {@code response}
     * @return how long to wait before trying again, or null to give up
     */
    @Nullable
    public Long delayBeforeRetryMs(final ApiResponse response, final int failedAttempts) {
        final boolean retryable = response.isTooManyRequests() ? retryTooManyRequests : response.isTransientFailure();
        final boolean retriesLeft = failedAttempts <= delaysMs.size();
        if (!retryable || !retriesLeft) {
            return null;
        }
        return delaysMs.get(failedAttempts - 1);
    }
}
