package app.runeshare.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class StartTaskSession {
    private int npcRunescapeId;
    private boolean leagues;
    private String accountType;
    private Integer worldMapXCoordinate;
    private Integer worldMapYCoordinate;
    private List<Integer> backpackRunescapeItemIds;
    private List<Integer> equipmentRunescapeItemIds;
}
