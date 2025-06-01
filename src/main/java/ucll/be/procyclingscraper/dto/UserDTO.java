package ucll.be.procyclingscraper.dto;

import lombok.Value;
import ucll.be.procyclingscraper.model.Role;

@Value
public class UserDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private Integer totalPoints;
}
