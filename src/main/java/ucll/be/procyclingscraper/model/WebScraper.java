package ucll.be.procyclingscraper.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WebScraper {
    private String title;
    private List<String> links;
    private String error;
}
