package ucll.be.procyclingscraper.service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.TimeResult;
import ucll.be.procyclingscraper.repository.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import javax.naming.spi.DirStateFactory.Result;

@Service
public class ResultService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    TimeResultRepository timeResultRepository;

    @Autowired
    StageRepository stageRepository;

    @Autowired
    CyclistRepository cyclistRepository;

    public Result scrapeResult(){
        List<Stage> stages = stageRepository.findAll();
        try{
            for(Stage stage: stages){
                Document doc = Jsoup.connect(stage.getStageUrl())
                    .userAgent(USER_AGENT)
                    .get();
                Elements resultRows = doc.select("results basic moblist10");

                for (Element row : resultRows) {
                    String position = row.selectFirst("td:first-child").text();

                    Element riderElement = row.selectFirst("td:nth-child(7) a");
                    String riderName = riderElement != null ? riderElement.text() : "Unknown";

                    Element timeElement = row.selectFirst("td.time.ar");
                    String time = timeElement != null ? timeElement.text() : "Unknown";

                    TimeResult timeResult = new TimeResult();
                    timeResult.setPosition(Integer.parseInt(position));
                    timeResult.setCyclist(cyclistRepository.findByName(riderName));
                    timeResult.setTime(LocalDateTime.parse(time));

                    timeResultRepository.save(timeResult);
                }

            }
            
        } catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }
    

}