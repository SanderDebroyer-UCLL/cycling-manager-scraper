package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderMessage {
    private List<UserDTO> users;
    private Long competitionId;
}