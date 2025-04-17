package ucll.be.procyclingscraper.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;
import java.util.List;

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
    private String niveau;
    private String startDate;
    private String endDate;
    private Integer distance;
    private String raceUrl;
    @OneToMany(mappedBy = "id", cascade = CascadeType.ALL)
    private List<Stage> stages;
}
