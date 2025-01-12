package app.runeshare.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class StopTaskSession {
    private int taskSessionId;
}
