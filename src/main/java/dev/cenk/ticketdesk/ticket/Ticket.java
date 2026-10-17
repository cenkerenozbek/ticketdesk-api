package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiException;
import dev.cenk.ticketdesk.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;

@Entity @Table(name = "tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 5000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Priority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TicketStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "creator_id", nullable = false)
    private AppUser creator;

    protected Ticket() {}

    public Ticket(String title, String description, Priority priority, AppUser creator) {
        this.title = title.strip();
        this.description = description.strip();
        this.priority = priority;
        this.creator = creator;
        this.status = TicketStatus.OPEN;
        // Match PostgreSQL precision so the POST response equals later reads.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public TicketStatus advanceTo(TicketStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "Allowed transitions: OPEN -> IN_PROGRESS -> RESOLVED");
        }
        TicketStatus previous = status;
        status = next;
        return previous;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Priority getPriority() { return priority; }
    public TicketStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public AppUser getCreator() { return creator; }
}
