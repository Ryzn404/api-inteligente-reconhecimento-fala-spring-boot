package dio.budgeting.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {
    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void should_createTransaction_when_dataIsValid() {
        var transaction = new Transaction("  Compras no mercado  ", 8050, Category.GROCERIES, TODAY);

        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getDescription()).isEqualTo("Compras no mercado");
        assertThat(transaction.getAmount()).isEqualTo(8050);
        assertThat(transaction.getCategory()).isEqualTo(Category.GROCERIES);
        assertThat(transaction.getDate()).isEqualTo(TODAY);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -100})
    void should_rejectTransaction_when_amountIsNotPositive(long amount) {
        assertThatThrownBy(() -> new Transaction("Farmácia", amount, Category.PHARMA, TODAY))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("O valor do gasto deve ser maior que zero.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void should_rejectTransaction_when_descriptionIsBlank(String description) {
        assertThatThrownBy(() -> new Transaction(description, 1000, Category.AUTO, TODAY))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A descrição do gasto é obrigatória.");
    }

    @Test
    void should_rejectTransaction_when_descriptionIsTooLong() {
        var description = "a".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1);

        assertThatThrownBy(() -> new Transaction(description, 1000, Category.AUTO, TODAY))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A descrição do gasto deve ter no máximo 255 caracteres.");
    }

    @Test
    void should_rejectTransaction_when_categoryIsMissing() {
        assertThatThrownBy(() -> new Transaction("Gasolina", 20000, null, TODAY))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A categoria do gasto é obrigatória.");
    }

    @Test
    void should_rejectTransaction_when_dateIsMissing() {
        assertThatThrownBy(() -> new Transaction("Gasolina", 20000, Category.AUTO, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A data do gasto é obrigatória.");
    }

    @Test
    void should_rejectTransaction_when_dateIsInTheFuture() {
        assertThatThrownBy(() -> new Transaction("Gasolina", 20000, Category.AUTO, TODAY.plusDays(1)))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A data do gasto não pode estar no futuro.");
    }

    @Test
    void should_acceptTransaction_when_dateIsInThePast() {
        var transaction = new Transaction("Jantar", 12000, Category.RESTAURANT, TODAY.minusDays(30));

        assertThat(transaction.getDate()).isEqualTo(TODAY.minusDays(30));
    }
}
