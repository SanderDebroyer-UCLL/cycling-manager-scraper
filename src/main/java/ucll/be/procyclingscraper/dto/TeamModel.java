package ucll.be.procyclingscraper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor
@NoArgsConstructor
public class TeamModel {
    
    private Long id; 
    private String name; 
    private String teamUrl;

}
