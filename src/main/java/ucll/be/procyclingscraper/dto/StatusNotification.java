package ucll.be.procyclingscraper.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ucll.be.procyclingscraper.model.CompetitionStatus;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class StatusNotification {
    private CompetitionStatus status;
}
