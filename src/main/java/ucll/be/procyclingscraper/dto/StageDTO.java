package ucll.be.procyclingscraper.dto;

import lombok.Value;
import ucll.be.procyclingscraper.model.ParcoursType;

@Value
public class StageDTO {
    private Long id;
    private String name;
    private String departure;
    private String arrival;
    private String date;
    private String startTime;
    private Double distance;
    private String stageUrl;
    private Double verticalMeters;
    private Enum<ParcoursType> parcoursType;
}
