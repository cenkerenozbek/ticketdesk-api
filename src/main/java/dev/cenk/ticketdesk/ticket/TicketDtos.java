package dev.cenk.ticketdesk.ticket;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class TicketDtos {
    private TicketDtos() {}

    public record CreateTicket(@NotBlank @Size(max = 160) String title,
                               @NotBlank @Size(max = 5000) String description,
                               @NotNull Priority priority) {}
    public record ChangeStatus(@NotNull TicketStatus status) {}
    public record CreateComment(@NotBlank @Size(max = 5000) String body) {}
    public record CommentResponse(Long id, Long ticketId, Long authorId, String body, Instant createdAt) {
        public static CommentResponse from(TicketComment comment) {
            return new CommentResponse(comment.getId(), comment.getTicket().getId(),
                    comment.getAuthor().getId(), comment.getBody(), comment.getCreatedAt());
        }
    }
    public record StatusCount(TicketStatus status, long count) {}
    public record TicketResponse(Long id, String title, String description, Priority priority,
                                 TicketStatus status, Instant createdAt, Long creatorId) {
        public static TicketResponse from(Ticket ticket) {
            return new TicketResponse(ticket.getId(), ticket.getTitle(), ticket.getDescription(),
                    ticket.getPriority(), ticket.getStatus(), ticket.getCreatedAt(), ticket.getCreator().getId());
        }
    }
    public record HistoryResponse(Long id, Long changedBy, TicketStatus fromStatus,
                                  TicketStatus toStatus, Instant changedAt) {
        public static HistoryResponse from(StatusHistory history) {
            return new HistoryResponse(history.getId(), history.getChangedBy().getId(),
                    history.getFromStatus(), history.getToStatus(), history.getChangedAt());
        }
    }
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
