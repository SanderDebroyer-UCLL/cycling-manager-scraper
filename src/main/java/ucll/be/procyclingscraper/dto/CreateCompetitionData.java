package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCompetitionData {
    
    private String name;
    private List<String> usernames;
    private List<String> raceIds;
}
