package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.domain.Transaction;
import com.example.account.dto.TransactionDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.repository.TransactionRepository;
import com.example.account.type.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.account.type.AccountStatus.*;
import static com.example.account.type.ErrorCode.*;
import static com.example.account.type.TransactionResultType.*;
import static com.example.account.type.TransactionType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void successUseBalance() {
        //given
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        TransactionDto transactionDto = transactionService.useBalance(1L, "10000000000", 200L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(200L, captor.getValue().getAmount());
        assertEquals(9800L, captor.getValue().getBalanceSnapshot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(USE, transactionDto.getTransactionType());
        assertEquals(9000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 유저 없음 - 잔액 사용 실패")
    void useBalance_UserNotFound() {
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "10000000000", 1000L));

        assertEquals(USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 실패")
    void useBalance_AccountNotFound() {
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "10000000000", 1000L));


        assertEquals(ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌 소유주 다름 - 잔액 사용 실패")
    void useBalanceFailed_UserUnMatch() {
        AccountUser pobi = AccountUser.builder()
                .name("Pobi").build();
        pobi.setId(12L);

        AccountUser harry = AccountUser.builder()
                .name("Pobi").build();
        harry.setId(13L);

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(harry)
                        .balance(0L)
                        .accountNumber("1000000012").build()));

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 1000L));

        assertEquals(USER_ACCOUNT_UN_MATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("해지 계좌는 사용할 수 없다")
    void useBalanceFailed_AlreadyUnregistered() {
        AccountUser pobi = AccountUser.builder()
                .name("Pobi").build();
        pobi.setId(12L);

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(pobi)
                        .accountStatus(AccountStatus.UNREGISTERED)
                        .balance(0L)
                        .accountNumber("1000000012").build()));

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 1000L));

        assertEquals(ACCOUNT_ALREADY_UNREGISTERED, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 금액이 계좌 잔액보다 큽니다")
    void exceedAmount_UseBalance() {
        //given
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(100L)
                .accountNumber("1000000012").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        //when
        //then
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 1000L));

        assertEquals(AMOUNT_EXCEED_BALANCE, exception.getErrorCode());
        verify(transactionRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("실패 트레잭션 저장 성공")
    void saveFailedUseTransaction() {
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        transactionService.saveFailedUseTransaction("10000000000", 200L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(200L, captor.getValue().getAmount());
        assertEquals(10000L, captor.getValue().getBalanceSnapshot());
        assertEquals(F, captor.getValue().getTransactionResultType());
    }

    @Test
    void successCancelBalance() {
        //given
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(200L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(CANCEL)
                        .transactionResultType(S)
                        .transactionId("transactionIdForCancel")
                        .transactedAt(LocalDateTime.now())
                        .amount(200L)
                        .balanceSnapshot(10000L)
                        .build());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        TransactionDto transactionDto = transactionService.cancelBalance("transactionId", "10000000000", 200L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(200L, captor.getValue().getAmount());
        assertEquals(10200L, captor.getValue().getBalanceSnapshot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(CANCEL, transactionDto.getTransactionType());
        assertEquals(10000L, transactionDto.getBalanceSnapshot());
        assertEquals(200L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 취소 실패")
    void cancelTransaction_AccountNotFound() {
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(Transaction.builder().build()));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "10000000000", 1000L));


        assertEquals(ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("원 사용 거래 없음 - 잔액 사용 취소 실패")
    void cancelTransaction_TransactionNotFound() {
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "10000000000", 1000L));


        assertEquals(TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래와 계좌 매칭 실패 - 잔액 사용 취소 실패")
    void cancelTransaction_TransactionAccountUnMatch() {
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        account.setId(1L);

        Account accountNotUse = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000013").build();
        accountNotUse.setId(2L);

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(200L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(accountNotUse));

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "10000000000", 200L));


        assertEquals(TRANSACTION_ACCOUNT_UN_MATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래금액과 취소 금액이 다름 - 잔액 사용 취소 실패")
    void cancelTransaction_CancelMustFully() {
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        account.setId(1L);

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1200L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "10000000000", 200L));


        assertEquals(CANCEL_MUST_FULLY, exception.getErrorCode());
    }

    @Test
    @DisplayName("취소는 1년까지만 가능 - 잔액 사용 취소 실패")
    void cancelTransaction_TooOldOrder() {
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        account.setId(1L);

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1).minusDays(1))
                .amount(200L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "10000000000", 200L));


        assertEquals(TOO_OLD_ORDER_TO_CANCEL, exception.getErrorCode());
    }

    @Test
    void successQueryTransaction() {
        AccountUser user = AccountUser.builder()
                .name("Pobi").build();
        user.setId(12L);

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        account.setId(1L);

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1))
                .amount(200L)
                .balanceSnapshot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        //when
        TransactionDto transactionDto = transactionService.queryTransaction("trxId");

        //then
        assertEquals(USE, transactionDto.getTransactionType());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(200, transactionDto.getAmount());
        assertEquals("transactionId", transactionDto.getTransactionId());
    }

    @Test
    @DisplayName("원 거래 없음 - 잔액 조회 실패")
    void queryTransaction_TransactionNotFound() {
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.queryTransaction("transactionId"));


        assertEquals(TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }
}