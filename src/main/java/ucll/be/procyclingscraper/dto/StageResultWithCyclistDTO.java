package ucll.be.procyclingscraper.dto;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import ucll.be.procyclingscraper.model.RaceStatus;
import ucll.be.procyclingscraper.model.ScrapeResultType;

@Data
@Builder
@AllArgsConstructor
public class StageResultWithCyclistDTO {
    private Long id;
    private String position;
    private RaceStatus raceStatus;
    private Duration time;
    private int point;
    private ScrapeResultType scrapeResultType;
    private int totalPoints;

    private Long cyclistId;
    private String cyclistName;
    private String cyclistCountry;
}
