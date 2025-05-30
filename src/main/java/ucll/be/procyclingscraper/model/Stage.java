package ucll.be.procyclingscraper.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Stage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String departure;
    private String arrival;
    private String date;
    private String startTime;
    private Double distance;
    private String stageUrl;
    private Double verticalMeters;
    private Enum<ParcoursType> parcoursType;
    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<StageResult> results = new ArrayList<>();

    public void addResult(StageResult result) {
        result.setStage(this);
        results.add(result);
    }
}
