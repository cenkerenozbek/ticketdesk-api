package dev.cenk.ticketdesk.user;

import dev.cenk.ticketdesk.ticket.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("demo")
public class DemoData implements CommandLineRunner {
    private final UserRepository users;
    private final TicketRepository tickets;
    private final PasswordEncoder passwords;

    public DemoData(UserRepository users, TicketRepository tickets, PasswordEncoder passwords) {
        this.users = users;
        this.tickets = tickets;
        this.passwords = passwords;
    }

    @Override @Transactional
    public void run(String... args) {
        seed("user@ticketdesk.local", Role.USER, "Kampüs Wi-Fi bağlantısı kesiliyor");
        seed("other@ticketdesk.local", Role.USER, "Kütüphane yazıcısı çalışmıyor");
        seed("agent@ticketdesk.local", Role.AGENT, null);
    }

    private void seed(String email, Role role, String title) {
        if (users.findByEmail(email).isPresent()) return;
        AppUser user = users.save(new AppUser(email, passwords.encode("demo-password"), role));
        if (title != null) tickets.save(new Ticket(title, "Yerel örnek destek talebi.", Priority.MEDIUM, user));
    }
}
