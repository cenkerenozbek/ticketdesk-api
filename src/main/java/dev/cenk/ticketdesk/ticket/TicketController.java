package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiException;
import dev.cenk.ticketdesk.security.TicketDeskPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static dev.cenk.ticketdesk.ticket.TicketDtos.*;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketService service;

    public TicketController(TicketService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicket request,
                                                  @AuthenticationPrincipal TicketDeskPrincipal actor) {
        TicketResponse result = service.create(request, actor);
        return ResponseEntity.created(URI.create("/api/tickets/" + result.id())).body(result);
    }

    @GetMapping
    public PageResponse<TicketResponse> list(@RequestParam(required = false) TicketStatus status,
                                              @RequestParam(required = false) Priority priority,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "createdAt,desc") String sort,
                                              @AuthenticationPrincipal TicketDeskPrincipal actor) {
        return service.list(status, priority, page(page, size, sort, Set.of("id", "createdAt", "title")), actor);
    }

    @GetMapping("/{id}")
    public TicketResponse detail(@PathVariable Long id, @AuthenticationPrincipal TicketDeskPrincipal actor) {
        return service.detail(id, actor);
    }

    @PatchMapping("/{id}/status")
    public TicketResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatus request,
                                        @AuthenticationPrincipal TicketDeskPrincipal actor) {
        return service.changeStatus(id, request.status(), actor);
    }

    @GetMapping("/{id}/history")
    public PageResponse<HistoryResponse> history(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @AuthenticationPrincipal TicketDeskPrincipal actor) {
        return service.history(id, page(page, size, "id,asc", Set.of("id")), actor);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody CreateComment request,
                                      @AuthenticationPrincipal TicketDeskPrincipal actor) {
        return service.addComment(id, request, actor);
    }

    @GetMapping("/{id}/comments")
    public PageResponse<CommentResponse> comments(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @AuthenticationPrincipal TicketDeskPrincipal actor) {
        return service.comments(id, page(page, size, "id,asc", Set.of("id")), actor);
    }

    private static Pageable page(int page, int size, String sort, Set<String> allowed) {
        String[] parts = sort.split(",", -1);
        if (page < 0 || size < 1 || size > 100 || parts.length != 2 || !allowed.contains(parts[0])
                || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                    "page >= 0; size 1..100; sort: id|createdAt|title,asc|desc");
        }
        Sort order = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
        if (!parts[0].equals("id")) order = order.and(Sort.by("id"));
        return PageRequest.of(page, size, order);
    }
}
