package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.Value;

@Value
public class MainReserveCyclistStagePointsDTO {
    private List<StagePointsPerUserPerCyclistDTO> mainCyclists;
    private List<StagePointsPerUserPerCyclistDTO> reserveCyclists;
}
