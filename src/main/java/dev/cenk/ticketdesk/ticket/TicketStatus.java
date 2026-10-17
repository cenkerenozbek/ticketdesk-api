package dev.cenk.ticketdesk.ticket;

public enum TicketStatus {
    OPEN, IN_PROGRESS, RESOLVED;

    public boolean canTransitionTo(TicketStatus next) {
        return (this == OPEN && next == IN_PROGRESS) || (this == IN_PROGRESS && next == RESOLVED);
    }
}
