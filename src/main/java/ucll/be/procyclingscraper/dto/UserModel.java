package ucll.be.procyclingscraper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserModel {
    
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
}
