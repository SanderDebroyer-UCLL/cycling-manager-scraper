package ucll.be.procyclingscraper.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PickNotification {
    private String cyclistName;
    private String email;
    private Long currentPick;
}