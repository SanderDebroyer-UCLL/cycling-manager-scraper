package ucll.be.procyclingscraper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProCyclingScraperApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProCyclingScraperApplication.class, args);
    }
}
