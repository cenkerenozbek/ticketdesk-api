package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiErrorHandler;
import dev.cenk.ticketdesk.security.*;
import dev.cenk.ticketdesk.user.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketReportController.class)
@Import({SecurityConfig.class, ApiErrorHandler.class})
class TicketReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TicketReportService service;
    @MockitoBean UserRepository users;
    private static final String PATH = "/api/reports/tickets-by-status";

    @Test void reportRequiresAuthentication() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void userCannotReadGlobalReport() throws Exception {
        var actor = new TicketDeskPrincipal(1L, "user@test.local", "", Role.USER);
        mvc.perform(get(PATH).with(user(actor))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test void agentCanReadReport() throws Exception {
        var actor = new TicketDeskPrincipal(3L, "agent@test.local", "", Role.AGENT);
        when(service.ticketsByStatus(actor)).thenReturn(List.of(new TicketDtos.StatusCount(TicketStatus.OPEN, 2)));
        mvc.perform(get(PATH).with(user(actor))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN")).andExpect(jsonPath("$[0].count").value(2));
    }
}
