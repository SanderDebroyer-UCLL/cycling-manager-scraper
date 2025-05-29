package ucll.be.procyclingscraper.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Setter
@Getter
@Value
public class PickNotification {
    private String cyclistName;
    private Long cyclistId;
    private String email;
    private Long currentPick;
}