package app.runeshare.api;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RuneShareTaskEvent {
    @NonNull
    private Integer taskSessionId;

    private int attackXp;
    private int strengthXp;
    private int defenceXp;
    private int rangedXp;
    private int magicXp;
    private int hitpointsXp;
    private int slayerXp;
}
