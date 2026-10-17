package dev.cenk.ticketdesk.ticket;

import com.jayway.jsonpath.JsonPath;
import dev.cenk.ticketdesk.security.TicketDeskPrincipal;
import dev.cenk.ticketdesk.user.*;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt in with -Ppostgres-it. Requires a dedicated PostgreSQL test database. */
@SpringBootTest
@AutoConfigureMockMvc
class TicketPostgresIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> env("TEST_DB_URL", "jdbc:postgresql://localhost:55432/ticketdesk_test"));
        properties.add("spring.datasource.username", () -> env("TEST_DB_USER", "ticketdesk"));
        properties.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", "local-test-db"));
    }
    private static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TicketRepository tickets;
    @Autowired CommentRepository comments;
    @Autowired TicketService service;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private AppUser owner;
    private AppUser other;
    private AppUser agent;
    private Long ticketId;
    private Long otherTicketId;

    @BeforeEach void fixtures() {
        String suffix = UUID.randomUUID().toString();
        String hash = passwords.encode("test-password");
        owner = users.saveAndFlush(new AppUser("owner-" + suffix + "@test.local", hash, Role.USER));
        other = users.saveAndFlush(new AppUser("other-" + suffix + "@test.local", hash, Role.USER));
        agent = users.saveAndFlush(new AppUser("agent-" + suffix + "@test.local", hash, Role.AGENT));
        ticketId = tickets.saveAndFlush(new Ticket("Wi-Fi", "Connection fails", Priority.HIGH, owner)).getId();
        otherTicketId = tickets.saveAndFlush(new Ticket("Printer", "Paper jam", Priority.LOW, other)).getId();
    }

    @AfterEach void cleanOwnFixtures() {
        if (owner == null || other == null || agent == null) return;
        jdbc.update("delete from comments where ticket_id in (select id from tickets where creator_id in (?, ?))", owner.getId(), other.getId());
        jdbc.update("delete from ticket_status_history where ticket_id in (select id from tickets where creator_id in (?, ?))", owner.getId(), other.getId());
        jdbc.update("delete from tickets where creator_id in (?, ?)", owner.getId(), other.getId());
        jdbc.update("delete from users where id in (?, ?, ?)", owner.getId(), other.getId(), agent.getId());
    }

    @Test void createPersistsWithAuthenticatedOwner() throws Exception {
        String body = mvc.perform(post("/api/tickets").with(httpBasic(owner.getEmail(), "test-password"))
                        .contentType("application/json").content("{\"title\":\"Laptop\",\"description\":\"Won't start\",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.creatorId").value(owner.getId()))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        String createdAt = JsonPath.read(body, "$.createdAt");
        assertThat(jdbc.queryForObject("select creator_id from tickets where id = ?", Long.class, id)).isEqualTo(owner.getId());
        mvc.perform(get("/api/tickets/" + id).with(httpBasic(owner.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdAt").value(createdAt));
    }

    @Test void listCombinesOwnershipFilterPaginationAndOrdering() throws Exception {
        tickets.saveAndFlush(new Ticket("Another Wi-Fi", "Slow connection", Priority.HIGH, owner));
        mvc.perform(get("/api/tickets?status=OPEN&priority=HIGH&size=1&sort=id,desc")
                        .with(httpBasic(owner.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Another Wi-Fi"));
        mvc.perform(get("/api/tickets?status=OPEN&priority=HIGH&size=1&page=1&sort=id,desc")
                        .with(httpBasic(owner.getEmail(), "test-password")))
                .andExpect(jsonPath("$.content[0].id").value(ticketId));
    }

    @Test void anotherUsersDetailAndHistoryAre404() throws Exception {
        for (String path : new String[]{"/api/tickets/" + ticketId, "/api/tickets/" + ticketId + "/history"}) {
            mvc.perform(get(path).with(httpBasic(other.getEmail(), "test-password")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
        }
    }

    @Test void agentTransitionCommitsStatusAndHistory() throws Exception {
        mvc.perform(patch("/api/tickets/" + ticketId + "/status").with(httpBasic(agent.getEmail(), "test-password"))
                        .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        assertThat(dbStatus()).isEqualTo("IN_PROGRESS");
        assertThat(historyCount()).isEqualTo(1);
        mvc.perform(get("/api/tickets/" + ticketId + "/history").with(httpBasic(owner.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].changedBy").value(agent.getId()))
                .andExpect(jsonPath("$.content[0].fromStatus").value("OPEN"));
    }

    @Test void invalidTransitionLeavesBothTablesUnchanged() throws Exception {
        mvc.perform(patch("/api/tickets/" + ticketId + "/status").with(httpBasic(agent.getEmail(), "test-password"))
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());
        assertThat(dbStatus()).isEqualTo("OPEN");
        assertThat(historyCount()).isZero();
    }

    @Test void transactionFailureRollsBackStatusAndHistoryTogether() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
            service.changeStatus(ticketId, TicketStatus.IN_PROGRESS, TicketDeskPrincipal.from(agent));
            tickets.flush(); // Force both SQL writes before the simulated failure.
            throw new IllegalStateException("Simulated downstream failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(dbStatus()).isEqualTo("OPEN");
        assertThat(historyCount()).isZero();
    }

    @Test @Timeout(30)
    void concurrentStatusRequestsProduceOneTransitionAndOneConflict() throws Exception {
        List<Future<Integer>> responses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                // Hold the row so both HTTP requests must overlap at the database lock.
                jdbc.queryForObject("select id from tickets where id = ? for update", Long.class, ticketId);
                Callable<Integer> change = () -> mvc.perform(patch("/api/tickets/" + ticketId + "/status")
                                .with(httpBasic(agent.getEmail(), "test-password"))
                                .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}"))
                        .andReturn().getResponse().getStatus();
                responses.add(pool.submit(change));
                responses.add(pool.submit(change));
                // Observe real PostgreSQL lock waits; no timing-based sleep or repository mocks.
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    assertThat(jdbc.queryForObject("""
                                select count(*) from pg_stat_activity
                                where datname = current_database() and wait_event_type = 'Lock'
                                  and query ilike '%tickets%' and pid <> pg_backend_pid()
                                """, Integer.class)).isEqualTo(2);
                });
            }); // Release the blocker: one request commits, the other sees IN_PROGRESS.
            assertThat(List.of(responses.get(0).get(10, TimeUnit.SECONDS),
                    responses.get(1).get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(dbStatus()).isEqualTo("IN_PROGRESS");
        assertThat(historyCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select from_status from ticket_status_history where ticket_id = ?",
                String.class, ticketId)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select to_status from ticket_status_history where ticket_id = ?",
                String.class, ticketId)).isEqualTo("IN_PROGRESS");
    }

    @Test void ownerAndAgentCommentsPersistAndPagesStayWithinTicket() throws Exception {
        comments.saveAndFlush(new TicketComment(tickets.getReferenceById(otherTicketId), other, "Private other comment"));
        String createdComment = mvc.perform(post("/api/tickets/" + ticketId + "/comments").with(httpBasic(owner.getEmail(), "test-password"))
                        .contentType("application/json").content("{\"body\":\"  İlk yorum  \",\"authorId\":" + other.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorId").value(owner.getId()))
                .andExpect(jsonPath("$.ticketId").value(ticketId)).andExpect(jsonPath("$.body").value("İlk yorum"))
                .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String commentCreatedAt = JsonPath.read(createdComment, "$.createdAt");
        mvc.perform(post("/api/tickets/" + ticketId + "/comments").with(httpBasic(agent.getEmail(), "test-password"))
                        .contentType("application/json").content("{\"body\":\"İnceliyorum\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorId").value(agent.getId()));
        assertThat(jdbc.queryForObject("select count(*) from comments where ticket_id = ?", Integer.class, ticketId)).isEqualTo(2);
        mvc.perform(get("/api/tickets/" + ticketId + "/comments?size=1").with(httpBasic(agent.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].body").value("İlk yorum"))
                .andExpect(jsonPath("$.content[0].createdAt").value(commentCreatedAt));
        mvc.perform(get("/api/tickets/" + ticketId + "/comments?size=1&page=1").with(httpBasic(owner.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].body").value("İnceliyorum"));
        mvc.perform(get("/api/tickets/" + ticketId + "/comments?size=1&page=2").with(httpBasic(owner.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test void otherUserCannotReadOrWriteComments() throws Exception {
        mvc.perform(post("/api/tickets/" + ticketId + "/comments").with(httpBasic(other.getEmail(), "test-password"))
                        .contentType("application/json").content("{\"body\":\"Unauthorized\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets/" + ticketId + "/comments").with(httpBasic(other.getEmail(), "test-password")))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from comments where ticket_id = ?", Integer.class, ticketId)).isZero();
    }

    @Test void missingTicketAndCommentsAre404() throws Exception {
        mvc.perform(get("/api/tickets/" + Long.MAX_VALUE).with(httpBasic(owner.getEmail(), "test-password")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
        String path = "/api/tickets/" + Long.MAX_VALUE + "/comments";
        mvc.perform(get(path).with(httpBasic(owner.getEmail(), "test-password"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(httpBasic(agent.getEmail(), "test-password"))
                        .contentType("application/json").content("{\"body\":\"Hello\"}"))
                .andExpect(status().isNotFound());
    }

    @Test void emptyCommentsReturnAnEmptyPage() throws Exception {
        mvc.perform(get("/api/tickets/" + ticketId + "/comments").with(httpBasic(owner.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void resolvedTicketsStillAcceptComments() throws Exception {
        var actor = TicketDeskPrincipal.from(agent);
        service.changeStatus(ticketId, TicketStatus.IN_PROGRESS, actor);
        service.changeStatus(ticketId, TicketStatus.RESOLVED, actor);
        mvc.perform(post("/api/tickets/" + ticketId + "/comments").with(httpBasic(owner.getEmail(), "test-password"))
                        .contentType("application/json").content("{\"body\":\"Teşekkürler\"}"))
                .andExpect(status().isCreated());
        assertThat(dbStatus()).isEqualTo("RESOLVED");
        assertThat(historyCount()).isEqualTo(2);
    }

    @Test void groupByReportCountsEveryOwnersTicketsAndIncludesZeroStatuses() throws Exception {
        var actor = TicketDeskPrincipal.from(agent);
        service.changeStatus(ticketId, TicketStatus.IN_PROGRESS, actor);
        mvc.perform(get("/api/reports/tickets-by-status").with(httpBasic(agent.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("OPEN")).andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[1].status").value("IN_PROGRESS")).andExpect(jsonPath("$[1].count").value(1))
                .andExpect(jsonPath("$[2].status").value("RESOLVED")).andExpect(jsonPath("$[2].count").value(0));
        service.changeStatus(otherTicketId, TicketStatus.IN_PROGRESS, actor);
        service.changeStatus(otherTicketId, TicketStatus.RESOLVED, actor);
        mvc.perform(get("/api/reports/tickets-by-status").with(httpBasic(agent.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].count").value(0))
                .andExpect(jsonPath("$[1].count").value(1)).andExpect(jsonPath("$[2].count").value(1));
    }

    @Test void emptyDatabaseReportContainsThreeZeroCounts() throws Exception {
        tickets.deleteAllById(java.util.List.of(ticketId, otherTicketId));
        mvc.perform(get("/api/reports/tickets-by-status").with(httpBasic(agent.getEmail(), "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].count").value(0)).andExpect(jsonPath("$[1].count").value(0))
                .andExpect(jsonPath("$[2].count").value(0));
    }

    private String dbStatus() {
        return jdbc.queryForObject("select status from tickets where id = ?", String.class, ticketId);
    }
    private int historyCount() {
        return jdbc.queryForObject("select count(*) from ticket_status_history where ticket_id = ?", Integer.class, ticketId);
    }
}
