package ucll.be.procyclingscraper.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;

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

    private LocalTime time;

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
    private List<StagePoints> stagePoints = new ArrayList<>();

    public int getTotalPoints() {
        return stagePoints.stream().mapToInt(StagePoints::getValue).sum();
    }
}
