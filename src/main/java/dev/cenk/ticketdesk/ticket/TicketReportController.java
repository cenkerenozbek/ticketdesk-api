package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.security.TicketDeskPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static dev.cenk.ticketdesk.ticket.TicketDtos.StatusCount;

@RestController
@RequestMapping("/api/reports")
public class TicketReportController {
    private final TicketReportService service;

    public TicketReportController(TicketReportService service) { this.service = service; }

    @GetMapping("/tickets-by-status")
    public List<StatusCount> ticketsByStatus(@AuthenticationPrincipal TicketDeskPrincipal actor) {
        return service.ticketsByStatus(actor);
    }
}
