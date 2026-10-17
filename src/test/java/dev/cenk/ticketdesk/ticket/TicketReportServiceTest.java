package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiException;
import dev.cenk.ticketdesk.security.TicketDeskPrincipal;
import dev.cenk.ticketdesk.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketReportServiceTest {
    @Test void serviceAlsoRejectsUserBeforeQueryingGlobalCounts() {
        TicketRepository tickets = mock(TicketRepository.class);
        var user = new TicketDeskPrincipal(1L, "user@test.local", "", Role.USER);
        assertThatThrownBy(() -> new TicketReportService(tickets).ticketsByStatus(user))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(tickets);
    }
}
