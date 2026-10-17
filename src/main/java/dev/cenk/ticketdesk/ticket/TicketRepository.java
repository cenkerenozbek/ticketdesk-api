package dev.cenk.ticketdesk.ticket;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    interface StatusTotal {
        String getStatus();
        long getTotal();
    }

    @Query(value = "SELECT status AS status, COUNT(*) AS total FROM tickets GROUP BY status", nativeQuery = true)
    List<StatusTotal> countTicketsByStatus();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findForUpdate(@Param("id") Long id);
}
