package com.bank.payments.security;

import com.bank.payments.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.util.Map;

@Configuration
public class SecurityConfig {

    // Existing APIs, still open without login so current API testing keeps working.
    // Lock these down (and check the customer owns the data) before going live.
    private static final String[] OPEN_APIS = {
            "/api/customers/**", "/api/accounts/**", "/api/transactions/**"
    };

    private static final String[] PUBLIC_PAGES = {
            "/", "/index.html", "/login.html", "/register.html", "/dashboard.html",
            "/css/**", "/js/**", "/favicon.ico", "/error"
    };

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService loginAttempts)
            throws Exception {
        http
                // Send anything arriving over plain HTTP to HTTPS (8080 -> 8443)
                .requiresChannel(channel -> channel.anyRequest().requiresSecure())
                .authorizeHttpRequests(auth -> auth
                        // dashboard.html itself is public; the data it loads from /api/me requires login
                        .requestMatchers(PUBLIC_PAGES).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(OPEN_APIS).permitAll()
                        .anyRequest().authenticated())
                // Session cookies need CSRF protection; the open APIs don't use the session
                .csrf(csrf -> csrf.ignoringRequestMatchers(OPEN_APIS))
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            loginAttempts.loginSucceeded(
                                    ((CustomerPrincipal) authentication.getPrincipal()).getCustomerId());
                            writeJson(response, HttpStatus.OK, Map.of("message", "Login successful."));
                        })
                        .failureHandler((request, response, exception) -> {
                            String message = loginAttempts.loginFailed(request.getParameter("email"), exception);
                            writeError(response, HttpStatus.UNAUTHORIZED, message);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Please log in."))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpStatus.FORBIDDEN, e instanceof CsrfException
                                        ? "Invalid or missing CSRF token. Fetch one from /api/auth/csrf."
                                        : "Access denied.")))
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        writeJson(response, status, new ErrorResponse(status.value(), message));
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
