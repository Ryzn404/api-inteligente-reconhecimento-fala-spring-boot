package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidTransactionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistTransactionUseCaseTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final PersistTransactionUseCase useCase = new PersistTransactionUseCase(repository);

    @Test
    void should_returnValueInReais_when_amountIsInformedInCents() {
        var output = useCase.execute(new PersistTransactionInput("Compras no mercado", 8050, Category.GROCERIES, null));

        assertThat(output.value()).isEqualTo(80.50);
        assertThat(output.category()).isEqualTo("GROCERIES");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void should_useTodaysDate_when_dateIsNotInformed() {
        var output = useCase.execute(new PersistTransactionInput("Compras no mercado", 8050, Category.GROCERIES, null));

        assertThat(output.date()).isEqualTo(LocalDate.now());
    }

    @Test
    void should_keepInformedDate() {
        var yesterday = LocalDate.now().minusDays(1);

        var output = useCase.execute(new PersistTransactionInput("Pizza", 6500, Category.RESTAURANT, yesterday));

        assertThat(output.date()).isEqualTo(yesterday);
    }

    @Test
    void should_notPersist_when_inputIsInvalid() {
        assertThatThrownBy(() -> useCase.execute(
                new PersistTransactionInput("Compras no mercado", 0, Category.GROCERIES, null)))
                .isInstanceOf(InvalidTransactionException.class);

        assertThat(repository.findAll()).isEmpty();
    }
}
