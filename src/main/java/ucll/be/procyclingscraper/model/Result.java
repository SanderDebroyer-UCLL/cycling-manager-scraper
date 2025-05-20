package ucll.be.procyclingscraper.model;

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
public abstract class Result {

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
}
