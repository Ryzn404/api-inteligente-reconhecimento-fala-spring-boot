package dio.budgeting.application;

import dio.budgeting.application.output.ExpensesSummaryOutput.CategoryTotal;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummarizeExpensesUseCaseTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final SummarizeExpensesUseCase useCase = new SummarizeExpensesUseCase(repository);

    @Test
    void should_sumExpensesByCategory_when_transactionsExist() {
        repository.save(new Transaction("Mercado do bairro", 8000, Category.GROCERIES));
        repository.save(new Transaction("Feira", 4050, Category.GROCERIES));
        repository.save(new Transaction("Remédio", 3990, Category.PHARMA));
        repository.save(new Transaction("Gasolina", 20000, Category.AUTO));

        var summary = useCase.execute();

        assertThat(summary.total()).isEqualTo(360.40);
        assertThat(summary.transactionCount()).isEqualTo(4);
        assertThat(summary.categories()).containsExactly(
                new CategoryTotal("AUTO", 1, 200.00),
                new CategoryTotal("GROCERIES", 2, 120.50),
                new CategoryTotal("PHARMA", 1, 39.90));
    }

    @Test
    void should_returnZeroTotal_when_thereAreNoTransactions() {
        var summary = useCase.execute();

        assertThat(summary.total()).isZero();
        assertThat(summary.transactionCount()).isZero();
        assertThat(summary.categories()).isEmpty();
    }
}
