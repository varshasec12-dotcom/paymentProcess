package com.bank.payments.security;

import com.bank.payments.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.util.Map;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

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
                        // Bank-staff operations. Everything else needs a login, and the controllers
                        // check that customers only touch their own customer record and accounts.
                        .requestMatchers(HttpMethod.GET, "/api/customers", "/api/accounts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/customers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/*").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            loginAttempts.loginSucceeded(customerId(authentication), request.getRemoteAddr());
                            writeJson(response, HttpStatus.OK, Map.of("message", "Login successful."));
                        })
                        .failureHandler((request, response, exception) -> {
                            String message = loginAttempts.loginFailed(request.getParameter("email"), exception,
                                    request.getRemoteAddr());
                            writeError(response, HttpStatus.UNAUTHORIZED, message);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                log.info("Logout: customerId={}, ip={}", customerId(authentication),
                                        request.getRemoteAddr());
                            }
                            response.setStatus(HttpStatus.OK.value());
                        }))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Please log in."))
                        .accessDeniedHandler((request, response, e) -> {
                            boolean csrf = e instanceof CsrfException;
                            log.warn("Access denied ({}): {} {}, customerId={}, ip={}",
                                    csrf ? "CSRF" : "forbidden", request.getMethod(), request.getRequestURI(),
                                    currentCustomerId(), request.getRemoteAddr());
                            writeError(response, HttpStatus.FORBIDDEN, csrf
                                    ? "Invalid or missing CSRF token. Fetch one from /api/auth/csrf."
                                    : "Access denied.");
                        }))
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private static Long customerId(Authentication authentication) {
        return authentication.getPrincipal() instanceof CustomerPrincipal principal
                ? principal.getCustomerId() : null;
    }

    private static Long currentCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : customerId(authentication);
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
