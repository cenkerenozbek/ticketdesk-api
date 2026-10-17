package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity @Table(name = "comments")
public class TicketComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false) private Ticket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false) private AppUser author;
    @Column(nullable = false, length = 5000) private String body;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected TicketComment() {}

    public TicketComment(Ticket ticket, AppUser author, String body) {
        this.ticket = ticket;
        this.author = author;
        this.body = body.strip();
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public AppUser getAuthor() { return author; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
