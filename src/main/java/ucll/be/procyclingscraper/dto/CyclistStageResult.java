package ucll.be.procyclingscraper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CyclistStageResult {
    private String name;
    private String stageUrl;
    private String position;
}
