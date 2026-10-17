package dev.cenk.ticketdesk.security;

import dev.cenk.ticketdesk.user.AppUser;
import dev.cenk.ticketdesk.user.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record TicketDeskPrincipal(Long id, String email, String passwordHash, Role role) implements UserDetails {
    public static TicketDeskPrincipal from(AppUser user) {
        return new TicketDeskPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole());
    }
    public boolean isAgent() { return role == Role.AGENT; }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
