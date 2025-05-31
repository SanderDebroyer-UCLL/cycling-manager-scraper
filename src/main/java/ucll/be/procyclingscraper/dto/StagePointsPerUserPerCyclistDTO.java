package ucll.be.procyclingscraper.dto;

import lombok.Value;

@Value
public class StagePointsPerUserPerCyclistDTO {
    private int points;
    private String cyclistName;
    private Long cyclistId;
    private Boolean isCyclistActive;
    private Long userId;
}
