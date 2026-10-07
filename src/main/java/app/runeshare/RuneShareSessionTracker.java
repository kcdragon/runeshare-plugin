package app.runeshare;

import app.runeshare.api.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.WorldType;

import javax.swing.SwingUtilities;
import java.util.EnumSet;
import java.util.List;

/**
 * Sessions are started and stopped on the EDT, and responses from RuneShare are
 * handed back to the EDT before touching the session or calling the handlers
 * passed in. {@code running} is also read by the client thread, hence volatile.
 */
@Slf4j
public class RuneShareSessionTracker {

    @NonNull
    private final RuneShareApi runeShareApi;

    @Getter
    private volatile boolean running = false;

    private Integer taskSessionId = null;

    @Setter
    private EnumSet<WorldType> worldTypes = null;

    @Setter
    private String accountType = null;

    @Setter
    private volatile List<Integer> backpackRunescapeItemIds = null;

    @Setter
    private volatile List<Integer> equipmentRunescapeItemIds = null;

    public RuneShareSessionTracker(RuneShareApi runeShareApi) {
        this.runeShareApi = runeShareApi;
    }

    public void start(final StartTaskSession startTaskSession, final StartTaskSessionResponseHandler startTaskSessionResponseHandler) {
        startTaskSession.setAccountType(accountType);
        // worldTypes is populated from the client thread, which may not have run
        // yet when a session is started, in which case this is not Leagues.
        startTaskSession.setLeagues(PlayerAccount.isLeagues(worldTypes));
        startTaskSession.setBackpackRunescapeItemIds(backpackRunescapeItemIds);
        startTaskSession.setEquipmentRunescapeItemIds(equipmentRunescapeItemIds);
        runeShareApi.startTaskSession(startTaskSession, new StartTaskSessionResponseHandler() {
            @Override
            public void onSuccess(StartTaskSessionResponse startTaskSessionResponse) {
                SwingUtilities.invokeLater(() -> {
                    running = true;
                    taskSessionId = startTaskSessionResponse.getTaskSessionId();
                    startTaskSessionResponseHandler.onSuccess(startTaskSessionResponse);
                });
            }

            @Override
            public void onFailure(String reason) {
                SwingUtilities.invokeLater(() -> startTaskSessionResponseHandler.onFailure(reason));
            }
        });
    }

    public void updateXp(int attackXp, int strengthXp, int defenceXp, int rangedXp, int magicXp, int hitpointsXp, int slayerXp) {
        if (!running) {
            return;
        }

        final RuneShareTaskEvent runeShareTaskEvent = RuneShareTaskEvent
                .builder()
                .taskSessionId(this.taskSessionId)
                .attackXp(attackXp)
                .strengthXp(strengthXp)
                .defenceXp(defenceXp)
                .rangedXp(rangedXp)
                .magicXp(magicXp)
                .hitpointsXp(hitpointsXp)
                .slayerXp(slayerXp)
                .build();

        runeShareApi.createTaskEvent(runeShareTaskEvent);
    }

    /**
     * Forgets the running session without telling RuneShare, for when the token
     * has been rejected and a stop request could never succeed.
     */
    public void abandon() {
        this.running = false;
        this.taskSessionId = null;
    }

    public void stop(StopTaskSessionResponseHandler stopTaskSessionResponseHandler) {
        this.running = false;

        final StopTaskSession stopTaskSession = StopTaskSession.builder().taskSessionId(this.taskSessionId).build();
        runeShareApi.stopTaskSession(stopTaskSession, new StopTaskSessionResponseHandler() {
            @Override
            public void onSuccess() {
                SwingUtilities.invokeLater(stopTaskSessionResponseHandler::onSuccess);
            }

            @Override
            public void onFailure(String reason) {
                SwingUtilities.invokeLater(() -> stopTaskSessionResponseHandler.onFailure(reason));
            }
        });
        this.taskSessionId = null;
    }
}
