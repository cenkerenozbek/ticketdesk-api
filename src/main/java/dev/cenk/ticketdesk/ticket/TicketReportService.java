package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiException;
import dev.cenk.ticketdesk.security.TicketDeskPrincipal;
import java.util.EnumMap;
import java.util.List;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.cenk.ticketdesk.ticket.TicketDtos.StatusCount;

@Service
@Transactional(readOnly = true)
public class TicketReportService {
    private final TicketRepository tickets;

    public TicketReportService(TicketRepository tickets) { this.tickets = tickets; }

    public List<StatusCount> ticketsByStatus(TicketDeskPrincipal actor) {
        if (!actor.isAgent()) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        var counts = new EnumMap<TicketStatus, Long>(TicketStatus.class);
        tickets.countTicketsByStatus().forEach(row -> counts.put(TicketStatus.valueOf(row.getStatus()), row.getTotal()));
        return Arrays.stream(TicketStatus.values())
                .map(status -> new StatusCount(status, counts.getOrDefault(status, 0L))).toList();
    }
}
