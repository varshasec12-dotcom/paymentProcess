package com.bank.payments.service;

import com.bank.payments.exception.AccountNotFoundException;
import com.bank.payments.exception.CoolingPeriodException;
import com.bank.payments.exception.InsufficientBalanceException;
import com.bank.payments.model.Account;
import com.bank.payments.model.Customer;
import com.bank.payments.model.Transaction.TransactionType;
import com.bank.payments.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final long coolingPeriodHours;
    private final BigDecimal coolingPeriodLimit;

    public AccountService(AccountRepository accountRepository,
                           CustomerService customerService,
                           TransactionService transactionService,
                           @Value("${payments.contact-change.cooling-period-hours}") long coolingPeriodHours,
                           @Value("${payments.contact-change.cooling-period-limit}") BigDecimal coolingPeriodLimit) {
        this.accountRepository = accountRepository;
        this.customerService = customerService;
        this.transactionService = transactionService;
        this.coolingPeriodHours = coolingPeriodHours;
        this.coolingPeriodLimit = coolingPeriodLimit;
    }

    // findCustomerById only returns ACTIVE customers, so a deactivated customer cannot open an account
    @Transactional
    public Account openAccount(Long customerId, String accountType) {
        Customer customer = customerService.findCustomerById(customerId);
        Account account = new Account(customer, generateAccountNumber(), accountType, BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    // 12-digit number from a database sequence. The existsById check skips any number that was
    // entered by hand before numbers were generated; save() would otherwise overwrite that account.
    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = String.format("%012d", accountRepository.nextAccountNumber());
        } while (accountRepository.existsById(accountNumber));
        return accountNumber;
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public List<Account> getAccountsByCustomer(Long customerId) {
        return accountRepository.findByCustomer_CustomerIdOrderByAccountNumber(customerId);
    }

    public Account findAccountByNumber(String accountNumber) {
        return accountRepository.findById(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
    }

    @Transactional
    public Account deposit(String accountNumber, BigDecimal amount) {
        requirePositive(amount, "Deposit");

        Account account = lockAccount(accountNumber);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        transactionService.recordTransaction(null, account, amount, TransactionType.DEPOSIT);
        return account;
    }

    @Transactional
    public Account withdraw(String accountNumber, BigDecimal amount) {
        requirePositive(amount, "Withdrawal");

        Account account = lockAccount(accountNumber);
        checkCoolingPeriod(account, amount);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance.");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        transactionService.recordTransaction(account, null, amount, TransactionType.WITHDRAW);
        return account;
    }

    @Transactional
    public void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        requirePositive(amount, "Transfer");

        // Always lock the two accounts in the same (alphabetical) order. If one transfer locked A then B
        // while another locked B then A, each would wait for the other forever (a deadlock).
        boolean fromFirst = fromAccountNumber.compareTo(toAccountNumber) <= 0;
        Account first = lockAccount(fromFirst ? fromAccountNumber : toAccountNumber);
        Account second = lockAccount(fromFirst ? toAccountNumber : fromAccountNumber);
        Account source = fromFirst ? first : second;
        Account destination = fromFirst ? second : first;
        checkCoolingPeriod(source, amount);

        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance.");
        }

        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));
        accountRepository.save(source);
        accountRepository.save(destination);
        transactionService.recordTransaction(source, destination, amount, TransactionType.TRANSFER);
    }

    // Loads the account and locks its row until the current transaction ends.
    // Only for methods that change the balance; read-only lookups use findAccountByNumber.
    private Account lockAccount(String accountNumber) {
        return accountRepository.findByIdForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
    }

    // After an email/phone change, large outgoing payments are blocked for a while in case
    // the change was made by someone who has taken over the customer's account.
    private void checkCoolingPeriod(Account account, BigDecimal amount) {
        LocalDateTime changedAt = account.getCustomer().getContactChangedAt();
        if (changedAt == null || amount.compareTo(coolingPeriodLimit) <= 0) {
            return;
        }
        LocalDateTime allowedFrom = changedAt.plusHours(coolingPeriodHours);
        if (LocalDateTime.now().isBefore(allowedFrom)) {
            throw new CoolingPeriodException("Contact details were changed recently. Payments above "
                    + coolingPeriodLimit + " are blocked until " + allowedFrom + ".");
        }
    }

    private void requirePositive(BigDecimal amount, String operation) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(operation + " amount must be positive.");
        }
    }
}
