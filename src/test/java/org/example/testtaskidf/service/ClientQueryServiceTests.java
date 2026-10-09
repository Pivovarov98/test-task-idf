package org.example.testtaskidf.service;

import java.util.stream.Stream;

import org.example.testtaskidf.repository.AccountRepository;
import org.example.testtaskidf.repository.ExpenseLimitHistoryRepository;
import org.example.testtaskidf.repository.LimitExceededTransactionRepository;
import org.example.testtaskidf.service.mapping.ExpenseLimitMapper;
import org.example.testtaskidf.service.mapping.LimitExceededTransactionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ClientQueryServiceTests {
    private final ExpenseLimitHistoryRepository historyRepository = mock(ExpenseLimitHistoryRepository.class);
    private final LimitExceededTransactionRepository exceededRepository =
            mock(LimitExceededTransactionRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ExpenseLimitMapper historyMapper = mock(ExpenseLimitMapper.class);
    private final LimitExceededTransactionMapper exceededMapper = mock(LimitExceededTransactionMapper.class);
    private final ExpenseLimitHistoryService history = new ExpenseLimitHistoryService(historyRepository, historyMapper);
    private final LimitExceededTransactionService exceeded = new LimitExceededTransactionService(
            exceededRepository, accounts, exceededMapper);

    @ParameterizedTest
    @MethodSource("invalidArguments")
    void directServiceCallsRejectInvalidInputBeforeAnyDatabaseAccess(String account, int page, int size) {
        assertThatThrownBy(() -> history.getAll(account, page, size)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(400));
        assertThatThrownBy(() -> exceeded.getAll(account, page, size)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(400));
        verifyNoInteractions(historyRepository, exceededRepository, accounts, historyMapper, exceededMapper);
    }

    @Test
    void unknownAccountDoesNotRunPageQueriesOrMapping() {
        assertThatThrownBy(() -> history.getAll("0000000123", 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(404));
        assertThatThrownBy(() -> exceeded.getAll("0000000123", 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(404));
        verifyNoInteractions(exceededRepository, historyMapper, exceededMapper);
        org.mockito.Mockito.verify(historyRepository).accountExists("0000000123");
        org.mockito.Mockito.verifyNoMoreInteractions(historyRepository);
        org.mockito.Mockito.verify(accounts).exists("0000000123");
        org.mockito.Mockito.verifyNoMoreInteractions(accounts);
    }

    private static Stream<Arguments> invalidArguments() {
        return Stream.of(Arguments.of(null, 0, 20), Arguments.of("123", 0, 20),
                Arguments.of("abcdefghij", 0, 20), Arguments.of("00000001234", 0, 20),
                Arguments.of("0000000123", -1, 20), Arguments.of("0000000123", 0, 0),
                Arguments.of("0000000123", 0, -1), Arguments.of("0000000123", 0, 101));
    }
}
