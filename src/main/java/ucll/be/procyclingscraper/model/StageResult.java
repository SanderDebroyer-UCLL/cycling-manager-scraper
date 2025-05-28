package ucll.be.procyclingscraper.model;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class StageResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String position;

    @Enumerated(EnumType.STRING)
    private RaceStatus raceStatus;

    @Enumerated(EnumType.STRING)
    private ScrapeResultType scrapeResultType;

    @ManyToOne
    @JsonBackReference
    private Cyclist cyclist;

    @ManyToOne
    @JsonBackReference
    private Stage stage;

    @OneToMany(mappedBy = "stageResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("stage_result_points")
    private Set<StagePoints> stagePoints = new HashSet<>();

    public int getTotalPoints() {
        return stagePoints.stream().mapToInt(StagePoints::getValue).sum();
    }
}
