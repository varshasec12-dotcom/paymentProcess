package com.bank.payments.security;

import com.bank.payments.IntegrationTest;
import com.bank.payments.model.Customer;
import com.bank.payments.model.Customer.CustomerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginTest extends IntegrationTest {

    private static final String EMAIL = "sathya@example.com";
    private static final String PASSWORD = "Sathya@123";
    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    private static final String LOCKED = "Too many failed attempts. Your account is locked until ";

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = createCustomer(EMAIL, "9876543210", PASSWORD);
    }

    // Login is a form post (Spring Security form login), not JSON
    private ResultActions login(String email, String password, MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .param("email", email)
                .param("password", password)
                .session(session)
                .with(csrf()));
    }

    private ResultActions login(String email, String password) throws Exception {
        return login(email, password, new MockHttpSession());
    }

    // ---- Successful login ----

    @Test
    void correctPasswordLogsIn() throws Exception {
        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful."));
    }

    @Test
    void emailIsNotCaseSensitive() throws Exception {
        login("SATHYA@Example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsFailedAttempts() throws Exception {
        customer.setFailedLoginAttempts(3);
        customerRepository.save(customer);

        login(EMAIL, PASSWORD).andExpect(status().isOk());

        assertThat(reload(customer).getFailedLoginAttempts()).isZero();
    }

    // Protects against session fixation: a session id planted before login is useless afterwards
    @Test
    void loginChangesTheSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idBeforeLogin = session.getId();

        login(EMAIL, PASSWORD, session).andExpect(status().isOk());

        assertThat(session.getId()).isNotEqualTo(idBeforeLogin);
    }

    // ---- Rejected login: always the same message, so the form cannot reveal who is a customer ----

    @Test
    void wrongPasswordIsRejected() throws Exception {
        login(EMAIL, "Wrong@123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    @Test
    void passwordIsCaseSensitive() throws Exception {
        login(EMAIL, "sathya@123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    @Test
    void unknownEmailGetsTheSameMessage() throws Exception {
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    @Test
    void customerWithoutOnlineBankingPasswordCannotLogIn() throws Exception {
        createCustomer("branch@example.com", "9876543211", null);

        login("branch@example.com", "")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    @Test
    void inactiveCustomerCannotLogIn() throws Exception {
        customer.setStatus(CustomerStatus.INACTIVE);
        customerRepository.save(customer);

        login(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    // ---- Lockout after repeated wrong passwords (payments.auth.max-login-attempts=5) ----

    @Test
    void wrongPasswordIsCounted() throws Exception {
        login(EMAIL, "Wrong@123");

        assertThat(reload(customer).getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void fifthWrongPasswordLocksTheAccount() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "Wrong@123").andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
        }

        login(EMAIL, "Wrong@123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(startsWith(LOCKED)));

        assertThat(reload(customer).getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void lockedAccountRejectsEvenTheCorrectPassword() throws Exception {
        customer.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        customerRepository.save(customer);

        login(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(startsWith(LOCKED)));
    }

    @Test
    void accountUnlocksWhenTheLockExpires() throws Exception {
        customer.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        customerRepository.save(customer);

        login(EMAIL, PASSWORD).andExpect(status().isOk());

        assertThat(reload(customer).getLockedUntil()).isNull();
    }

    // ---- Request format ----

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login").param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or missing CSRF token. Fetch one from /api/auth/csrf."));
    }

    @Test
    void jsonBodyIsNotAccepted() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + EMAIL + "\", \"password\": \"" + PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    // ---- Session after login ----

    @Test
    void apiRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please log in."));
    }

    @Test
    void loggedInCustomerSeesOwnProfileWithoutSecrets() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(EMAIL, PASSWORD, session).andExpect(status().isOk());

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.failedLoginAttempts").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(EMAIL, PASSWORD, session).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
