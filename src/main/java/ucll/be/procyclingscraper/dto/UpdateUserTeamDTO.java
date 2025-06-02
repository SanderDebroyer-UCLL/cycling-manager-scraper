package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.Getter;
import lombok.Value;

@Getter
@Value
public class UpdateUserTeamDTO {
    List<String> mainCyclistIds;
    List<String> reserveCyclistIds;
    int currentStage;
}
