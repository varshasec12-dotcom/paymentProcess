package com.bank.payments;

import com.bank.payments.model.Customer;
import com.bank.payments.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Base for tests that send requests through the full application (security filters, controllers,
// services) against the payments_test database. See src/test/resources/application-test.properties.
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    protected CustomerRepository customerRepository;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpIntegrationTest() {
        // Every test starts with empty tables; CASCADE also empties accounts, transactions, etc.
        jdbcTemplate.execute("TRUNCATE customers RESTART IDENTITY CASCADE");

        // Send every request as HTTPS, otherwise SecurityConfig redirects it
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").secure(true))
                .build();
    }

    // password null = created by bank staff and not yet registered for online banking
    protected Customer createCustomer(String email, String phoneNumber, String password) {
        Customer customer = new Customer("Test", "Customer", email, phoneNumber);
        if (password != null) {
            customer.setPasswordHash(passwordEncoder.encode(password));
        }
        return customerRepository.save(customer);
    }

    protected Customer reload(Customer customer) {
        return customerRepository.findById(customer.getCustomerId()).orElseThrow();
    }
}
