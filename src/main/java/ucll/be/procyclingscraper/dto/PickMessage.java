package ucll.be.procyclingscraper.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PickMessage {
    private Long cyclistId;
    private String email;
    private Long competitionId;
}
