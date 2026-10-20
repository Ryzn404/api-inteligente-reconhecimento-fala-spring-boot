package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidTransactionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistTransactionUseCaseTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final PersistTransactionUseCase useCase = new PersistTransactionUseCase(repository);

    @Test
    void should_returnValueInReais_when_amountIsInformedInCents() {
        var output = useCase.execute(new PersistTransactionInput("Compras no mercado", 8050, Category.GROCERIES));

        assertThat(output.value()).isEqualTo(80.50);
        assertThat(output.category()).isEqualTo("GROCERIES");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void should_notPersist_when_inputIsInvalid() {
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("Compras no mercado", 0, Category.GROCERIES)))
                .isInstanceOf(InvalidTransactionException.class);

        assertThat(repository.findAll()).isEmpty();
    }
}
