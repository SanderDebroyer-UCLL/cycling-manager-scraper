package ucll.be.procyclingscraper.model;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "competition_pick")
public class CompetitionPick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "competition_id", nullable = false)
    @JsonBackReference // prevents recursion
    private Competition competition;

    private Long userId;

    private Long pickOrder;
}
