package app.runeshare.api;

import lombok.Value;

import javax.annotation.Nullable;
import java.time.LocalTime;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Sends bank tabs to RuneShare, retrying transient failures, and remembers how
 * the latest one went so the panel can show it.
 * <p>
 * Only the latest tab matters: syncing a new one abandons any retry still
 * pending for the previous one. Each sync gets a generation number, and a
 * response or retry from an older generation is ignored, otherwise a slow
 * request for an old tab could report "Synced" over the newer one.
 * <p>
 * Responses arrive on OkHttp threads and retries on the scheduler's, so state is
 * guarded by this object's lock. The listener is called outside it.
 */
public class BankTabSync {
    public interface Sender {
        void send(RuneShareBankTab bankTab, Consumer<ApiResponse> onResponse);
    }

    public interface Scheduler {
        Future<?> schedule(Runnable task, long delayMs);
    }

    public enum State {
        SYNCING,
        SYNCED,
        RETRYING,
        FAILED
    }

    @Value
    public static class Status {
        State state;

        String tag;

        @Nullable
        LocalTime syncedAt;

        @Nullable
        String failure;
    }

    private final Sender sender;

    private final Scheduler scheduler;

    private Runnable listener = () -> {};

    private int generation = 0;

    @Nullable
    private Future<?> pendingRetry = null;

    @Nullable
    private Status status = null;

    public BankTabSync(Sender sender, Scheduler scheduler) {
        this.sender = sender;
        this.scheduler = scheduler;
    }

    public synchronized void setListener(final Runnable listener) {
        this.listener = listener;
    }

    /**
     * @return how the latest sync went, or null if nothing has been synced
     */
    @Nullable
    public synchronized Status getStatus() {
        return status;
    }

    public void sync(final RuneShareBankTab bankTab) {
        final int syncGeneration;
        synchronized (this) {
            cancelPendingRetry();
            syncGeneration = ++generation;
            status = new Status(State.SYNCING, bankTab.getTag(), null, null);
        }
        notifyListener();

        attempt(syncGeneration, bankTab, 1);
    }

    /**
     * Abandons any pending retry, and ignores responses still in flight.
     */
    public synchronized void cancel() {
        cancelPendingRetry();
        generation++;
    }

    private void attempt(final int syncGeneration, final RuneShareBankTab bankTab, final int attemptNumber) {
        sender.send(bankTab, response -> onResponse(syncGeneration, bankTab, attemptNumber, response));
    }

    private void onResponse(final int syncGeneration, final RuneShareBankTab bankTab, final int attemptNumber, final ApiResponse response) {
        synchronized (this) {
            if (syncGeneration != generation) {
                return;
            }

            final String tag = bankTab.getTag();
            final Long retryDelayMs = response.isSuccessful() ? null : RetryPolicy.BANK_TAB.delayBeforeRetryMs(response, attemptNumber);
            if (response.isSuccessful()) {
                status = new Status(State.SYNCED, tag, LocalTime.now(), null);
            } else if (retryDelayMs != null) {
                status = new Status(State.RETRYING, tag, null, response.describeFailure());
                pendingRetry = scheduler.schedule(() -> retry(syncGeneration, bankTab, attemptNumber + 1), retryDelayMs);
            } else {
                status = new Status(State.FAILED, tag, null, response.describeFailure());
            }
        }
        notifyListener();
    }

    private void retry(final int syncGeneration, final RuneShareBankTab bankTab, final int attemptNumber) {
        synchronized (this) {
            if (syncGeneration != generation) {
                return;
            }
            pendingRetry = null;
        }

        attempt(syncGeneration, bankTab, attemptNumber);
    }

    private void cancelPendingRetry() {
        if (pendingRetry != null) {
            pendingRetry.cancel(false);
            pendingRetry = null;
        }
    }

    private void notifyListener() {
        final Runnable listenerToNotify;
        synchronized (this) {
            listenerToNotify = listener;
        }
        listenerToNotify.run();
    }
}
