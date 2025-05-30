package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.Value;

@Value
public class UserTeamDTO {
    private Long id;
    private String name;
    private Long competitionId;
    private List<CyclistDTO> mainCyclists;
    private List<CyclistDTO> reserveCyclists;
    private UserDTO user;
}
