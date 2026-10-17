package dev.cenk.ticketdesk.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryRepository extends JpaRepository<StatusHistory, Long> {
    Page<StatusHistory> findByTicketId(Long ticketId, Pageable pageable);
}
