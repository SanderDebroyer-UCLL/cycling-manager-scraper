package ucll.be.procyclingscraper.dto;

import lombok.Value;

@Value
public class StagePointsPerUserPerCyclistDTO {
    private int points;
    private String cyclistName;
    private Long userId;
}
