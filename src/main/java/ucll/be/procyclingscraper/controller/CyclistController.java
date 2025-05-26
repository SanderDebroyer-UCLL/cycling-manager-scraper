package ucll.be.procyclingscraper.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.service.CyclistService;

import java.util.List;


@RestController
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app/")
@RequestMapping("/cyclists")
public class CyclistController {

    private final CyclistService cyclistService;

    public CyclistController(CyclistService cyclistService) {
        this.cyclistService = cyclistService;
    }

    @GetMapping("/scrape")
    public List<Cyclist> scrapeCyclists() {
        return cyclistService.scrapeCyclists();
    }

    @GetMapping()
    public List<Cyclist> getCyclists() {
        return cyclistService.getCyclists();
    }
    
}
