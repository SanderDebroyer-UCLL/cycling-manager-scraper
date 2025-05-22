package ucll.be.procyclingscraper.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ucll.be.procyclingscraper.model.CompetitionStatus;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatusMessage {
    private CompetitionStatus status;
    private Long competitionId;
}
