package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiException;
import dev.cenk.ticketdesk.security.TicketDeskPrincipal;
import dev.cenk.ticketdesk.user.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketServiceTest {
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final HistoryRepository history = mock(HistoryRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final TicketService service = new TicketService(tickets, users, history, comments);
    private final TicketDeskPrincipal owner = new TicketDeskPrincipal(1L, "user@test.local", "", Role.USER);
    private final TicketDeskPrincipal agent = new TicketDeskPrincipal(3L, "agent@test.local", "", Role.AGENT);
    private AppUser creator;
    private Ticket ticket;

    @BeforeEach void setUp() {
        creator = mock(AppUser.class);
        when(creator.getId()).thenReturn(1L);
        ticket = new Ticket("Wi-Fi", "No connection", Priority.HIGH, creator);
    }

    @Test void creationUsesAuthenticatedOwnerAndAlwaysStartsOpen() {
        when(users.getReferenceById(1L)).thenReturn(creator);
        when(tickets.save(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(new TicketDtos.CreateTicket("Wi-Fi", "No connection", Priority.HIGH), owner);
        assertThat(result.creatorId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
    }

    @Test void hidesAnotherUsersTicket() {
        when(tickets.findById(7L)).thenReturn(Optional.of(ticket));
        var other = new TicketDeskPrincipal(2L, "other@test.local", "", Role.USER);
        assertThatThrownBy(() -> service.detail(7L, other)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test void historyAlsoChecksOwnership() {
        when(tickets.findById(7L)).thenReturn(Optional.of(ticket));
        var other = new TicketDeskPrincipal(2L, "other@test.local", "", Role.USER);
        assertThatThrownBy(() -> service.history(7L, org.springframework.data.domain.PageRequest.of(0, 20), other))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(history);
    }

    @Test void userCannotChangeStatus() {
        assertThatThrownBy(() -> service.changeStatus(7L, TicketStatus.IN_PROGRESS, owner))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(tickets, history);
    }

    @Test void agentChangeIncludesAuditRecord() {
        when(tickets.findForUpdate(7L)).thenReturn(Optional.of(ticket));
        AppUser staff = mock(AppUser.class);
        when(users.getReferenceById(3L)).thenReturn(staff);
        assertThat(service.changeStatus(7L, TicketStatus.IN_PROGRESS, agent).status()).isEqualTo(TicketStatus.IN_PROGRESS);
        var captured = org.mockito.ArgumentCaptor.forClass(StatusHistory.class);
        verify(history).save(captured.capture());
        assertThat(captured.getValue().getFromStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(captured.getValue().getToStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(captured.getValue().getChangedBy()).isSameAs(staff);
    }

    @Test void invalidTransitionDoesNotWriteHistory() {
        when(tickets.findForUpdate(7L)).thenReturn(Optional.of(ticket));
        assertThatThrownBy(() -> service.changeStatus(7L, TicketStatus.RESOLVED, agent)).isInstanceOf(ApiException.class);
        verifyNoInteractions(history);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test void missingTicketIs404() {
        when(tickets.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(99L, owner)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test void anotherUserCannotAddOrReadComments() {
        when(tickets.findById(7L)).thenReturn(Optional.of(ticket));
        var other = new TicketDeskPrincipal(2L, "other@test.local", "", Role.USER);
        assertThatThrownBy(() -> service.addComment(7L, new TicketDtos.CreateComment("Hello"), other))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.comments(7L, org.springframework.data.domain.PageRequest.of(0, 20), other))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(comments, users);
    }
}
