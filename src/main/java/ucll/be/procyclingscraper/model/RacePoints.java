package ucll.be.procyclingscraper.model;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RacePoints {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int value;

    private String reason; // "Race Parais-Roubaix 1st place",

    private Long raceId; // The ID of the

    @JsonBackReference("competition_race_points")
    @ManyToOne
    @JoinColumn(name = "competition_id")
    private Competition competition;

    @JsonBackReference("race_result_points")
    @ManyToOne
    @JoinColumn(name = "race_result_id")
    private RaceResult raceResult;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference("user_race_points")
    private User user;
}
