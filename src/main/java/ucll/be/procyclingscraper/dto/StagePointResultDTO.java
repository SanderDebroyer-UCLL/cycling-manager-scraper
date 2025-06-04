package ucll.be.procyclingscraper.dto;

import lombok.Value;

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
