package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.Value;

@Value
public class RaceDTO {
    private Long id;
    private String name;
    private String niveau;
    private String startDate;
    private String endDate;
    private Integer distance;
    private String raceUrl;
    private List<Long> competitionIds;
    private List<StageDTO> stages;
}
