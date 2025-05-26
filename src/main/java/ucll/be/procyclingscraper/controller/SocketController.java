package ucll.be.procyclingscraper.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import ucll.be.procyclingscraper.dto.CountMessage;
import ucll.be.procyclingscraper.dto.CountNotification;
import ucll.be.procyclingscraper.dto.OrderMessage;
import ucll.be.procyclingscraper.dto.OrderNotification;
import ucll.be.procyclingscraper.dto.PickMessage;
import ucll.be.procyclingscraper.dto.PickNotification;
import ucll.be.procyclingscraper.dto.StatusMessage;
import ucll.be.procyclingscraper.dto.StatusNotification;
import ucll.be.procyclingscraper.service.CompetitionService;
import ucll.be.procyclingscraper.service.UserTeamService;

@Controller
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app/")
public class SocketController {

    @Autowired
    private UserTeamService userTeamService;

    @Autowired
    private CompetitionService competitionService;

    @MessageMapping("/status")
    @SendTo("/topic/status")
    public StatusNotification handleStatus(StatusMessage statusMessage) {
        return competitionService.updateCompetitionStatus(statusMessage.getStatus(), statusMessage.getCompetitionId());
    }

    @MessageMapping("/order")
    @SendTo("/topic/order")
    public OrderNotification handleOrder(OrderMessage orderMessage) {
        return competitionService.updateOrderToCompetition(orderMessage.getUsers(), orderMessage.getCompetitionId());
    }

    @MessageMapping("/pick")    
    @SendTo("/topic/picks")
    public PickNotification handlePick(PickMessage pickMessage) {
        return userTeamService.addCyclistToUserTeam(pickMessage.getEmail(), pickMessage.getCyclistId() ,pickMessage.getCompetitionId());
    }

    @MessageMapping("/count")
    @SendTo("/topic/count")
    public CountNotification handleCount(CountMessage countMessage) {
        return competitionService.handleCyclistCount(countMessage.getMaxMainCyclists(), countMessage.getMaxReserveCyclists(), countMessage.getCompetitionId());
    }
}

