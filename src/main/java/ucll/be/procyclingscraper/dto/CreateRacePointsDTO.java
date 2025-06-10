package ucll.be.procyclingscraper.dto;

import lombok.Value;

@Value
public class CreateRacePointsDTO {
    private Long raceId;
    private Long competitionId;
    private Integer value;
    private String reason;

}
