package ucll.be.procyclingscraper.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;
import java.util.List;
import ucll.be.procyclingscraper.model.Cyclist;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Race {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    // private Boolean oneDay;
    // private List<Stage> Stages;
    // private List<Cyclist> startList;
}
