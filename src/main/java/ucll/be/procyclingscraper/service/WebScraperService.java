package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import ucll.be.procyclingscraper.model.WebScraper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class WebScraperService {

    public WebScraper scrape(String url) {
        WebScraper result = new WebScraper();
        List<String> links = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(url).get();
            result.setTitle(doc.title());

            Elements anchorTags = doc.select("a[href]");
            for (Element link : anchorTags) {
                links.add(link.attr("abs:href"));
            }

            result.setLinks(links);
        } catch (IOException e) {
            result.setError("Failed to scrape: " + e.getMessage());
        }

        return result;
    }
}
