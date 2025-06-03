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
public class StagePoints {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int value;

    private String reason; // e.g., "Stage 1 finish", "Mountain points", etc.

    private Long stageId; // The ID of the stage this point is associated with

    @JsonBackReference("competition_stage_points")
    @ManyToOne
    @JoinColumn(name = "competition_id")
    private Competition competition;

    @JsonBackReference("stage_result_points")
    @ManyToOne
    @JoinColumn(name = "stage_result_id")
    private StageResult stageResult;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference("user_stage_points")
    private User user;
}
