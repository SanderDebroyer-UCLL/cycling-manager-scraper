package ucll.be.procyclingscraper.dto;

import java.util.Set;

import lombok.Value;
import ucll.be.procyclingscraper.model.CompetitionPick;
import ucll.be.procyclingscraper.model.CompetitionStatus;

@Value
public class CompetitionDTO {
    private Long id;
    private String name;
    private Set<RaceDTO> races;
    public Set<UserDTO> users;
    private CompetitionStatus competitionStatus;
    private int maxMainCyclists;
    private int maxReserveCyclists;
    private Long currentPick;
    private Set<CompetitionPick> competitionPicks;
}
