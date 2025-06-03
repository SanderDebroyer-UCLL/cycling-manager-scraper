package ucll.be.procyclingscraper.dto;

import org.springframework.lang.Nullable;

import lombok.Value;

@Value
public class PointsPerUserPerCyclistDTO {
    private int points;
    private String cyclistName;
    private Long cyclistId;

    @Nullable
    private String reason;

    private Boolean isCyclistActive;
    private Long userId;
}
