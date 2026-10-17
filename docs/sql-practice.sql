-- Run against the local demo database using psql.
-- These read-only exercises use the actual TicketDesk schema.
\set user_email 'user@ticketdesk.local'

-- 1. A user's active tickets (OPEN and IN_PROGRESS).
SELECT t.id, t.title, t.status, t.created_at
FROM tickets t
JOIN users u ON u.id = t.creator_id
WHERE u.email = :'user_email' AND t.status IN ('OPEN', 'IN_PROGRESS')
ORDER BY t.created_at DESC, t.id DESC;

-- 2. Tickets and their creator, using an explicit JOIN.
SELECT t.id, t.title, u.email AS creator_email
FROM tickets t
JOIN users u ON u.id = t.creator_id
ORDER BY t.id;

-- 3. Counts for statuses that exist in the data.
-- The API additionally fills missing statuses with zero.
SELECT status, COUNT(*) AS total
FROM tickets
GROUP BY status
ORDER BY status;

-- 4. Tickets without comments, using a correlated subquery.
SELECT t.id, t.title
FROM tickets t
WHERE NOT EXISTS (SELECT 1 FROM comments c WHERE c.ticket_id = t.id)
ORDER BY t.id;

-- 5. The latest ten tickets, with a deterministic tie-breaker.
SELECT id, title, created_at
FROM tickets
ORDER BY created_at DESC, id DESC
LIMIT 10 OFFSET 0;
