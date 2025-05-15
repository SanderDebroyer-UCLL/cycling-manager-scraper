package ucll.be.procyclingscraper.model;

import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TimeResult extends Result{
    private LocalDateTime time;
}
