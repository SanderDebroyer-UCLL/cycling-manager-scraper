package ucll.be.procyclingscraper.dto;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ucll.be.procyclingscraper.model.CompetitionPick;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderNotification {
    private Set<CompetitionPick> competitionPicks;
}
