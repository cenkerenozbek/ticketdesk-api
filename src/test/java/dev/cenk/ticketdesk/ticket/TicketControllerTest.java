package dev.cenk.ticketdesk.ticket;

import dev.cenk.ticketdesk.common.ApiErrorHandler;
import dev.cenk.ticketdesk.security.*;
import dev.cenk.ticketdesk.user.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, ApiErrorHandler.class})
class TicketControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TicketService service;
    @MockitoBean UserRepository users;
    private final TicketDeskPrincipal owner = new TicketDeskPrincipal(1L, "user@test.local", "", Role.USER);

    @Test void missingAuthenticationIsJson401() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test void incorrectPasswordIs401() throws Exception {
        when(users.findByEmail("missing@test.local")).thenReturn(Optional.empty());
        mvc.perform(get("/api/tickets").with(httpBasic("missing@test.local", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test void blankTitleIs400() throws Exception {
        mvc.perform(post("/api/tickets").with(user(owner)).contentType("application/json")
                        .content("{\"title\":\" \",\"description\":\"Problem\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.title").exists());
        verifyNoInteractions(service);
    }

    @Test void invalidEnumIs400() throws Exception {
        mvc.perform(get("/api/tickets?status=UNKNOWN").with(user(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test void unboundedPageSizeIs400() throws Exception {
        mvc.perform(get("/api/tickets?size=101").with(user(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        verifyNoInteractions(service);
    }

    @Test void unknownSortFieldIs400() throws Exception {
        mvc.perform(get("/api/tickets?sort=passwordHash,asc").with(user(owner)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void userCannotPatchStatus() throws Exception {
        mvc.perform(patch("/api/tickets/7/status").with(user(owner)).contentType("application/json")
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test void agentCannotCreateUserTickets() throws Exception {
        var agent = new TicketDeskPrincipal(3L, "agent@test.local", "", Role.AGENT);
        mvc.perform(post("/api/tickets").with(user(agent)).contentType("application/json")
                        .content("{\"title\":\"Wi-Fi\",\"description\":\"Problem\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void createReturns201AndLocation() throws Exception {
        when(service.create(any(), eq(owner))).thenReturn(new TicketDtos.TicketResponse(7L, "Wi-Fi", "Problem",
                Priority.HIGH, TicketStatus.OPEN, java.time.Instant.parse("2026-10-18T00:00:00Z"), 1L));
        mvc.perform(post("/api/tickets").with(user(owner)).contentType("application/json")
                        .content("{\"title\":\"Wi-Fi\",\"description\":\"Problem\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/tickets/7"))
                .andExpect(jsonPath("$.creatorId").value(1)).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test void unsupportedMethodRemains405() throws Exception {
        mvc.perform(delete("/api/tickets/7").with(user(owner)))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"body\":null}", "{\"body\":\"\"}", "{\"body\":\"   \\n\\t\"}"})
    void blankCommentIs400(String json) throws Exception {
        mvc.perform(post("/api/tickets/7/comments").with(user(owner)).contentType("application/json").content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.body").exists());
        verifyNoInteractions(service);
    }

    @Test void oversizedCommentIs400() throws Exception {
        mvc.perform(post("/api/tickets/7/comments").with(user(owner)).contentType("application/json")
                        .content("{\"body\":\"" + "x".repeat(5001) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.body").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101"})
    void invalidCommentPageIs400(String query) throws Exception {
        mvc.perform(get("/api/tickets/7/comments?" + query).with(user(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        verifyNoInteractions(service);
    }

    @Test void commentRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/tickets/7/comments").contentType("application/json").content("{\"body\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tickets/7/comments")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void unexpectedFailureDoesNotExposeDatabaseDetails() throws Exception {
        when(service.detail(7L, owner)).thenThrow(new IllegalStateException("jdbc password=do-not-expose"));
        mvc.perform(get("/api/tickets/7").with(user(owner)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("do-not-expose"))));
    }
}
