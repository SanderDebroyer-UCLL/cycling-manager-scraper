package ucll.be.procyclingscraper.model;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RaceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String position;

    private Duration time;

    @Enumerated(EnumType.STRING)
    private RaceStatus raceStatus;

    @ManyToOne
    @JoinColumn(name = "race_id", referencedColumnName = "id")
    @JsonBackReference
    private Race race;

    @ManyToOne
    @JoinColumn(name = "cyclist_id", referencedColumnName = "id")
    @JsonBackReference
    private Cyclist cyclist;

    @OneToMany(mappedBy = "raceResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("race_result_points")
    private Set<RacePoints> racePoints = new HashSet<>();
}
