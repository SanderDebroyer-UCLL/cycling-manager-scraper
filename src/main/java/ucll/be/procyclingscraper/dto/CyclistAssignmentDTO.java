package ucll.be.procyclingscraper.dto;

import lombok.Value;
import ucll.be.procyclingscraper.model.CyclistRole;

@Value
public class CyclistAssignmentDTO {
    private Long id;
    private CyclistDTO cyclist;
    private CyclistRole role;
    private Integer fromStage;
    private Integer toStage;

}
