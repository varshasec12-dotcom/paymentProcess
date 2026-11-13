package com.bank.payments.service;

import com.bank.payments.exception.AccountNotFoundException;
import com.bank.payments.exception.CoolingPeriodException;
import com.bank.payments.exception.DuplicateAccountException;
import com.bank.payments.exception.InsufficientBalanceException;
import com.bank.payments.exception.TransferLimitExceededException;
import com.bank.payments.model.Account;
import com.bank.payments.model.Customer;
import com.bank.payments.model.Customer.CustomerStatus;
import com.bank.payments.model.Transaction.TransactionType;
import com.bank.payments.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final long coolingPeriodHours;
    private final BigDecimal coolingPeriodLimit;
    private final BigDecimal dailyTransferLimit;

    public AccountService(AccountRepository accountRepository,
                           CustomerService customerService,
                           TransactionService transactionService,
                           @Value("${payments.contact-change.cooling-period-hours}") long coolingPeriodHours,
                           @Value("${payments.contact-change.cooling-period-limit}") BigDecimal coolingPeriodLimit,
                           @Value("${payments.transfer.daily-limit}") BigDecimal dailyTransferLimit) {
        this.accountRepository = accountRepository;
        this.customerService = customerService;
        this.transactionService = transactionService;
        this.coolingPeriodHours = coolingPeriodHours;
        this.coolingPeriodLimit = coolingPeriodLimit;
        this.dailyTransferLimit = dailyTransferLimit;
    }

    // findCustomerById only returns ACTIVE customers, so a deactivated customer cannot open an account
    @Transactional
    public Account openAccount(Long customerId, String accountType) {
        Customer customer = customerService.findCustomerById(customerId);
        // One account of each type per customer. Two requests at the same moment can both pass this
        // check; the unique index in db/one_account_per_type_schema.sql stops the second one.
        if (accountRepository.existsByCustomer_CustomerIdAndAccountType(customerId, accountType)) {
            throw new DuplicateAccountException(
                    "You already have a " + accountType.toLowerCase(Locale.ROOT) + " account.");
        }
        Account account = accountRepository.save(
                new Account(customer, generateAccountNumber(), accountType, BigDecimal.ZERO));
        log.info("Account opened: customerId={}, account={}, type={}",
                customerId, account.getAccountNumber(), accountType);
        return account;
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

    // Same as findAccountByNumber, but only finds the customer's own accounts
    public Account findOwnedAccount(String accountNumber, Long customerId) {
        Account account = findAccountByNumber(accountNumber);
        requireOwner(account, customerId);
        return account;
    }

    @Transactional
    public Account deposit(String accountNumber, BigDecimal amount, Long customerId) {
        requirePositive(amount, "Deposit");

        Account account = lockAccount(accountNumber);
        requireOwner(account, customerId);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        transactionService.recordTransaction(null, account, amount, TransactionType.DEPOSIT);
        log.info("Deposit: customerId={}, account={}, amount={}", customerId, accountNumber, amount);
        return account;
    }

    @Transactional
    public Account withdraw(String accountNumber, BigDecimal amount, Long customerId) {
        requirePositive(amount, "Withdrawal");

        Account account = lockAccount(accountNumber);
        requireOwner(account, customerId);
        checkCoolingPeriod(account, amount);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance.");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        transactionService.recordTransaction(account, null, amount, TransactionType.WITHDRAW);
        log.info("Withdrawal: customerId={}, account={}, amount={}", customerId, accountNumber, amount);
        return account;
    }

    @Transactional
    // Money can only leave the customer's own account; it can go to anyone's
    public void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount, Long customerId) {
        requirePositive(amount, "Transfer");
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }

        // Always lock the two accounts in the same (alphabetical) order. If one transfer locked A then B
        // while another locked B then A, each would wait for the other forever (a deadlock).
        boolean fromFirst = fromAccountNumber.compareTo(toAccountNumber) <= 0;
        Account first = lockAccount(fromFirst ? fromAccountNumber : toAccountNumber);
        Account second = lockAccount(fromFirst ? toAccountNumber : fromAccountNumber);
        Account source = fromFirst ? first : second;
        Account destination = fromFirst ? second : first;
        requireOwner(source, customerId);
        requireActiveOwner(destination);
        checkCoolingPeriod(source, amount);
        checkDailyTransferLimit(source, amount);

        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance.");
        }

        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));
        accountRepository.save(source);
        accountRepository.save(destination);
        transactionService.recordTransaction(source, destination, amount, TransactionType.TRANSFER);
        log.info("Transfer: customerId={}, from={}, to={}, amount={}",
                customerId, fromAccountNumber, toAccountNumber, amount);
    }

    // Loads the account and locks its row until the current transaction ends.
    // Only for methods that change the balance; read-only lookups use findAccountByNumber.
    private Account lockAccount(String accountNumber) {
        return accountRepository.findByIdForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
    }

    // Same message as a missing account, so nobody can probe which account numbers exist
    private void requireOwner(Account account, Long customerId) {
        if (!account.getCustomer().getCustomerId().equals(customerId)) {
            log.warn("Customer tried to use an account they do not own: customerId={}, account={}",
                    customerId, account.getAccountNumber());
            throw new AccountNotFoundException("Account not found.");
        }
    }

    // A deactivated customer's accounts cannot receive money. Same message as a missing account,
    // so the response does not reveal that the account exists.
    private void requireActiveOwner(Account account) {
        if (account.getCustomer().getStatus() != CustomerStatus.ACTIVE) {
            log.warn("Transfer to an account of a deactivated customer: account={}", account.getAccountNumber());
            throw new AccountNotFoundException("Account not found.");
        }
    }

    // The source account is locked by the caller, so two transfers from the same account
    // cannot both pass this check.
    private void checkDailyTransferLimit(Account account, BigDecimal amount) {
        BigDecimal sentToday = transactionService.transferredToday(account.getAccountNumber());
        if (sentToday.add(amount).compareTo(dailyTransferLimit) > 0) {
            BigDecimal remaining = dailyTransferLimit.subtract(sentToday).max(BigDecimal.ZERO);
            log.warn("Transfer blocked by daily limit: account={}, amount={}, sentToday={}",
                    account.getAccountNumber(), amount, sentToday);
            throw new TransferLimitExceededException("Daily transfer limit of " + dailyTransferLimit
                    + " exceeded. You can transfer up to " + remaining + " more today.");
        }
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
            log.warn("Payment blocked by cooling period: customerId={}, account={}, amount={}, allowedFrom={}",
                    account.getCustomer().getCustomerId(), account.getAccountNumber(), amount, allowedFrom);
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
