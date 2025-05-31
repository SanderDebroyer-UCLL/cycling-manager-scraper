package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.Value;

@Value
public class UserTeamDTO {
    private Long id;
    private String name;
    private Long competitionId;
    private List<CyclistAssignmentDTO> cyclistAssignments;
    private UserDTO user;
}
