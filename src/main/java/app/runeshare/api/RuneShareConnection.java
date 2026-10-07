package app.runeshare.api;

import app.runeshare.RuneShareConfig;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;

/**
 * Whether RuneShare is accepting the configured API token, and whose it is.
 * <p>
 * Responses arrive on OkHttp threads, so all state is guarded by this object's
 * lock. The listener runs on whichever thread reported the change and is called
 * outside the lock.
 */
@Singleton
public class RuneShareConnection {
    private final RuneShareConfig runeShareConfig;

    private ConnectionStatus status = ConnectionStatus.UNKNOWN;

    @Nullable
    private CurrentUser currentUser = null;

    private Runnable listener = () -> {};

    @Inject
    public RuneShareConnection(RuneShareConfig runeShareConfig) {
        this.runeShareConfig = runeShareConfig;
    }

    public synchronized ConnectionStatus getStatus() {
        return status;
    }

    @Nullable
    public synchronized CurrentUser getCurrentUser() {
        return currentUser;
    }

    public synchronized void setListener(final Runnable listener) {
        this.listener = listener;
    }

    public void reset() {
        final Runnable listenerToNotify;
        synchronized (this) {
            status = ConnectionStatus.UNKNOWN;
            currentUser = null;
            listenerToNotify = listener;
        }
        listenerToNotify.run();
    }

    /**
     * @param requestApiToken the token the request was sent with
     */
    public void report(@Nullable final String requestApiToken, final ApiResponse response) {
        update(requestApiToken, response, null);
    }

    public void reportCurrentUser(@Nullable final String requestApiToken, final ApiResponse response, @Nullable final CurrentUser user) {
        update(requestApiToken, response, user);
    }

    private void update(@Nullable final String requestApiToken, final ApiResponse response, @Nullable final CurrentUser user) {
        final Runnable listenerToNotify;
        synchronized (this) {
            // A request sent with a token the player has since replaced says
            // nothing about the new one, however late it finishes.
            final boolean sentWithReplacedToken = !Objects.equals(requestApiToken, runeShareConfig.apiToken());
            if (sentWithReplacedToken) {
                return;
            }

            final ConnectionStatus nextStatus = status.after(response);
            final CurrentUser nextUser = user != null ? user : currentUser;
            if (nextStatus == status && Objects.equals(nextUser, currentUser)) {
                return;
            }

            status = nextStatus;
            currentUser = nextUser;
            listenerToNotify = listener;
        }
        listenerToNotify.run();
    }
}
