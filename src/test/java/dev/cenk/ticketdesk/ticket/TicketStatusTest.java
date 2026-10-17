package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiException;
import dev.cenk.ticketdesk.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class TicketStatusTest {
    @ParameterizedTest
    @CsvSource({"OPEN,OPEN,false", "OPEN,IN_PROGRESS,true", "OPEN,RESOLVED,false",
            "IN_PROGRESS,OPEN,false", "IN_PROGRESS,IN_PROGRESS,false", "IN_PROGRESS,RESOLVED,true",
            "RESOLVED,OPEN,false", "RESOLVED,IN_PROGRESS,false", "RESOLVED,RESOLVED,false"})
    void onlyForwardSingleStepTransitionsAreAllowed(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test void rejectedTransitionDoesNotMutateTicket() {
        Ticket ticket = new Ticket(" Wi-Fi ", " Connection fails ", Priority.HIGH,
                new AppUser("user@example.test", "hash", Role.USER));
        assertThatThrownBy(() -> ticket.advanceTo(TicketStatus.RESOLVED))
                .isInstanceOf(ApiException.class).hasMessageContaining("Allowed transitions");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getTitle()).isEqualTo("Wi-Fi");
    }
}
