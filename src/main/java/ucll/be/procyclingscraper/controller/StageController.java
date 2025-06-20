package ucll.be.procyclingscraper.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.StageDTO;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.service.StageService;

import java.util.List;

@RestController
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app")
@RequestMapping("/stages")
public class StageController {

    @Autowired
    private StageService stageService;

    @GetMapping("/scrape")
    public List<Stage> scrapeStages() {
        return stageService.scrapeStages();
    }

    @GetMapping()
    public List<StageDTO> getStages() {
        return stageService.getStages();
    }
}
