package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity @Table(name = "ticket_status_history")
public class StatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_id", nullable = false) private Ticket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "changed_by", nullable = false) private AppUser changedBy;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", nullable = false, length = 16) private TicketStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 16) private TicketStatus toStatus;
    @Column(name = "changed_at", nullable = false) private Instant changedAt;

    protected StatusHistory() {}
    public StatusHistory(Ticket ticket, AppUser changedBy, TicketStatus fromStatus, TicketStatus toStatus) {
        this.ticket = ticket;
        this.changedBy = changedBy;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public AppUser getChangedBy() { return changedBy; }
    public TicketStatus getFromStatus() { return fromStatus; }
    public TicketStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
}
