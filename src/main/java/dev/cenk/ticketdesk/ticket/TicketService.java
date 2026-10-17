package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiException;
import dev.cenk.ticketdesk.security.TicketDeskPrincipal;
import dev.cenk.ticketdesk.user.UserRepository;
import java.util.Objects;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.cenk.ticketdesk.ticket.TicketDtos.*;

@Service
@Transactional(readOnly = true)
public class TicketService {
    private final TicketRepository tickets;
    private final UserRepository users;
    private final HistoryRepository history;
    private final CommentRepository comments;

    public TicketService(TicketRepository tickets, UserRepository users, HistoryRepository history, CommentRepository comments) {
        this.tickets = tickets;
        this.users = users;
        this.history = history;
        this.comments = comments;
    }

    @Transactional
    public TicketResponse create(CreateTicket request, TicketDeskPrincipal actor) {
        if (actor.isAgent()) throw forbidden();
        Ticket ticket = new Ticket(request.title(), request.description(), request.priority(),
                users.getReferenceById(actor.id()));
        return TicketResponse.from(tickets.save(ticket));
    }

    public PageResponse<TicketResponse> list(TicketStatus status, Priority priority, Pageable page,
                                              TicketDeskPrincipal actor) {
        Specification<Ticket> visible = (root, query, cb) -> actor.isAgent()
                ? cb.conjunction() : cb.equal(root.get("creator").get("id"), actor.id());
        if (status != null) visible = visible.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (priority != null) visible = visible.and((root, query, cb) -> cb.equal(root.get("priority"), priority));
        return PageResponse.from(tickets.findAll(visible, page).map(TicketResponse::from));
    }

    public TicketResponse detail(Long id, TicketDeskPrincipal actor) {
        return TicketResponse.from(visibleTicket(id, actor));
    }

    @Transactional
    public TicketResponse changeStatus(Long id, TicketStatus next, TicketDeskPrincipal actor) {
        if (!actor.isAgent()) throw forbidden();
        // Serialize concurrent changes so a second request sees the committed status.
        Ticket ticket = tickets.findForUpdate(id).orElseThrow(ApiException::notFound);
        TicketStatus previous = ticket.advanceTo(next);
        history.save(new StatusHistory(ticket, users.getReferenceById(actor.id()), previous, next));
        // JPA dirty checking writes the status in the same transaction as the history row.
        return TicketResponse.from(ticket);
    }

    public PageResponse<HistoryResponse> history(Long id, Pageable page, TicketDeskPrincipal actor) {
        visibleTicket(id, actor);
        return PageResponse.from(history.findByTicketId(id, page).map(HistoryResponse::from));
    }

    @Transactional
    public CommentResponse addComment(Long id, CreateComment request, TicketDeskPrincipal actor) {
        Ticket ticket = visibleTicket(id, actor);
        TicketComment comment = new TicketComment(ticket, users.getReferenceById(actor.id()), request.body());
        return CommentResponse.from(comments.save(comment));
    }

    public PageResponse<CommentResponse> comments(Long id, Pageable page, TicketDeskPrincipal actor) {
        visibleTicket(id, actor);
        return PageResponse.from(comments.findByTicketId(id, page).map(CommentResponse::from));
    }

    private Ticket visibleTicket(Long id, TicketDeskPrincipal actor) {
        Ticket ticket = tickets.findById(id).orElseThrow(ApiException::notFound);
        if (!actor.isAgent() && !Objects.equals(ticket.getCreator().getId(), actor.id())) {
            throw ApiException.notFound();
        }
        return ticket;
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }
}
