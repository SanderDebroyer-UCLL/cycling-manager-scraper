package ucll.be.procyclingscraper.dto;

import lombok.Value;

@Value
public class CreateStagePointsDTO {
    private Long stageId;
    private Long competitionId;
    private Integer value;
    private String reason;

}
