package ucll.be.procyclingscraper.dto;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import ucll.be.procyclingscraper.model.RaceStatus;


@Data
@Builder
@AllArgsConstructor
public class RaceResultWithCyclistDTO {
    private Long id;
    private String position;
    private Duration time;
    private RaceStatus raceStatus;
    
    private Long cyclistId;
    private String cyclistName;
    private String cyclistCountry;
}
