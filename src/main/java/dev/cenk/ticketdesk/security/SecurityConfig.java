package dev.cenk.ticketdesk.security;

import dev.cenk.ticketdesk.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService users(UserRepository users) {
        return email -> users.findByEmail(email).map(TicketDeskPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        // A local CLI API: no cookies or sessions. Browser use needs a separate CSRF design.
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/tickets").hasRole("USER")
                        .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/status").hasRole("AGENT")
                        .requestMatchers("/api/reports/**").hasRole("AGENT")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401);
                    response.setHeader("WWW-Authenticate", "Basic realm=\"TicketDesk\"");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"fields\":{}}");
                }))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, ex) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":403,\"code\":\"FORBIDDEN\",\"message\":\"Access denied\",\"fields\":{}}");
                }))
                .build();
    }
}
