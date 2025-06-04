package ucll.be.procyclingscraper.dto;

import lombok.Value;
import ucll.be.procyclingscraper.model.ParcoursType;

@Value
public class StagePointResultDTO {
    private Long id;
    private String position;
    private String riderName;
    private Integer points;
    private Long stageId;
    private String StageName;
    private String stageUrl;
}
