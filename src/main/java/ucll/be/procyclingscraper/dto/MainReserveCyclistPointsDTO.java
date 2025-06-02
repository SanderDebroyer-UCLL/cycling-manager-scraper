package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.Value;

@Value
public class MainReserveCyclistPointsDTO {
    private List<PointsPerUserPerCyclistDTO> mainCyclists;
    private List<PointsPerUserPerCyclistDTO> reserveCyclists;
}
