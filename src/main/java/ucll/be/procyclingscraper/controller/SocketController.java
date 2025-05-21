package ucll.be.procyclingscraper.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import ucll.be.procyclingscraper.dto.OrderMessage;
import ucll.be.procyclingscraper.dto.OrderNotification;
import ucll.be.procyclingscraper.dto.PickMessage;
import ucll.be.procyclingscraper.dto.PickNotification;
import ucll.be.procyclingscraper.service.CompetitionService;
import ucll.be.procyclingscraper.service.UserTeamService;

@Controller
public class SocketController {

    @Autowired
    private UserTeamService userTeamService;

    @Autowired
    private CompetitionService competitionService;

    @MessageMapping("/order")
    @SendTo("/topic/order")
    public OrderNotification handleOrder(OrderMessage orderMessage) {
        return competitionService.updateOrderToCompetition(orderMessage.getUsers(), orderMessage.getCompetitionId());
    }

    @MessageMapping("/pick")    
    @SendTo("/topic/picks")
    public PickNotification handlePick(PickMessage message) {
        return userTeamService.addCyclistToUserTeam(message.getEmail(), message.getCyclistId() ,message.getCompetitionId());
    }
}

