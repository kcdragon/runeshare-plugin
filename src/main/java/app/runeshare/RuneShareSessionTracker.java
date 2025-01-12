package app.runeshare;

import app.runeshare.api.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.WorldType;

import java.util.EnumSet;

@Slf4j
public class RuneShareSessionTracker {

    @NonNull
    private final RuneShareApi runeShareApi;

    @Getter
    private boolean running = false;

    private Integer taskSessionId = null;

    @Setter
    private EnumSet<WorldType> worldTypes = null;

    @Setter
    private String accountType = null;

    public RuneShareSessionTracker(RuneShareApi runeShareApi) {
        this.runeShareApi = runeShareApi;
    }

    public void start(NPC npc, StartTaskSessionResponseHandler startTaskSessionResponseHandler) {
        runeShareApi.startTaskSession(npc, accountType, isLeaguesWorld(), startTaskSessionResponse -> {
            this.running = true;
            this.taskSessionId = startTaskSessionResponse.getTaskSessionId();
            startTaskSessionResponseHandler.onSuccess(startTaskSessionResponse);
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

    public void stop(StopTaskSessionResponseHandler stopTaskSessionResponseHandler) {
        this.running = false;

        final StopTaskSession stopTaskSession = StopTaskSession.builder().taskSessionId(this.taskSessionId).build();
        runeShareApi.stopTaskSession(stopTaskSession, stopTaskSessionResponseHandler);
        this.taskSessionId = null;
    }

    private boolean isLeaguesWorld()
    {
        return (worldTypes.contains(WorldType.SEASONAL) && !worldTypes.contains(WorldType.DEADMAN));
    }
}
