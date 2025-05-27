package ucll.be.procyclingscraper.dto;

import lombok.Value;

@Value
public class StagePointsPerUserDTO {
    private int points;
    private String fullName;
    private Long userId;
}
