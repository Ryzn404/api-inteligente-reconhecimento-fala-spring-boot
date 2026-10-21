package dio.budgeting.application;

import dio.budgeting.application.input.SummarizeExpensesInput;
import dio.budgeting.application.output.ExpensesSummaryOutput.CategoryTotal;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidPeriodException;
import dio.budgeting.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummarizeExpensesUseCaseTest {
    private static final LocalDate SEP_30 = LocalDate.of(2025, 9, 30);
    private static final LocalDate OCT_1 = LocalDate.of(2025, 10, 1);
    private static final LocalDate OCT_15 = LocalDate.of(2025, 10, 15);
    private static final LocalDate OCT_31 = LocalDate.of(2025, 10, 31);

    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final SummarizeExpensesUseCase useCase = new SummarizeExpensesUseCase(repository);

    @Test
    void should_sumExpensesByCategory_when_transactionsExist() {
        repository.save(new Transaction("Mercado do bairro", 8000, Category.GROCERIES, OCT_1));
        repository.save(new Transaction("Feira", 4050, Category.GROCERIES, OCT_1));
        repository.save(new Transaction("Remédio", 3990, Category.PHARMA, OCT_1));
        repository.save(new Transaction("Gasolina", 20000, Category.AUTO, OCT_1));

        var summary = useCase.execute(null);

        assertThat(summary.total()).isEqualTo(360.40);
        assertThat(summary.transactionCount()).isEqualTo(4);
        assertThat(summary.categories()).containsExactly(
                new CategoryTotal("AUTO", 1, 200.00),
                new CategoryTotal("GROCERIES", 2, 120.50),
                new CategoryTotal("PHARMA", 1, 39.90));
    }

    @Test
    void should_onlyCountExpensesInsidePeriod() {
        repository.save(new Transaction("Mercado de setembro", 10000, Category.GROCERIES, SEP_30));
        repository.save(new Transaction("Mercado", 8000, Category.GROCERIES, OCT_1));
        repository.save(new Transaction("Pizza", 6500, Category.RESTAURANT, OCT_15));
        repository.save(new Transaction("Gasolina", 20000, Category.AUTO, OCT_31));

        var summary = useCase.execute(new SummarizeExpensesInput(OCT_1, OCT_15));

        assertThat(summary.from()).isEqualTo(OCT_1);
        assertThat(summary.to()).isEqualTo(OCT_15);
        assertThat(summary.total()).isEqualTo(145.00);
        assertThat(summary.categories()).containsExactly(
                new CategoryTotal("GROCERIES", 1, 80.00),
                new CategoryTotal("RESTAURANT", 1, 65.00));
    }

    @Test
    void should_rejectPeriod_when_startIsAfterEnd() {
        assertThatThrownBy(() -> useCase.execute(new SummarizeExpensesInput(OCT_31, OCT_1)))
                .isInstanceOf(InvalidPeriodException.class);
    }

    @Test
    void should_returnZeroTotal_when_thereAreNoTransactions() {
        var summary = useCase.execute(null);

        assertThat(summary.total()).isZero();
        assertThat(summary.transactionCount()).isZero();
        assertThat(summary.categories()).isEmpty();
    }
}
