package ucll.be.procyclingscraper.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "cyclist_assignment")
public class CyclistAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_team_id")
    @JsonBackReference
    private UserTeam userTeam;

    @ManyToOne
    @JsonManagedReference
    @JoinColumn(name = "cyclist_id")
    private Cyclist cyclist;

    // 'MAIN' or 'RESERVE'
    @Enumerated(EnumType.STRING)
    private CyclistRole role;

    // Stage when this cyclist was added
    private Integer fromStage;

    // Stage when this cyclist was removed (nullable if still active)
    private Integer toStage;
}
