package ucll.be.procyclingscraper.dto;

import lombok.Value;

@Value
public class PointsPerUserPerCyclistDTO {
    private int points;
    private String cyclistName;
    private Long cyclistId;
    private Boolean isCyclistActive;
    private Long userId;
}
