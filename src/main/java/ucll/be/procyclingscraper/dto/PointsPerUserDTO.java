package ucll.be.procyclingscraper.dto;

import lombok.Value;

@Value
public class PointsPerUserDTO {
    private int points;
    private String fullName;
    private Long userId;
}
