package dio.budgeting.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class Transaction {
    // Tamanho padrão da coluna gerada pelo JPA para String (VARCHAR(255)).
    public static final int MAX_DESCRIPTION_LENGTH = 255;

    private TransactionId id;
    private String description;
    private long amount;
    private Category category;
    private LocalDate date;

    public Transaction(String description, long amount, Category category, LocalDate date) {
        validate(description, amount, category, date);
        this.id = new TransactionId();
        this.description = description.strip();
        this.amount = amount;
        this.category = category;
        this.date = date;
    }

    private static void validate(String description, long amount, Category category, LocalDate date) {
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("A descrição do gasto é obrigatória.");
        }
        if (description.strip().length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidTransactionException(
                    "A descrição do gasto deve ter no máximo " + MAX_DESCRIPTION_LENGTH + " caracteres.");
        }
        if (amount <= 0) {
            throw new InvalidTransactionException("O valor do gasto deve ser maior que zero.");
        }
        if (category == null) {
            throw new InvalidTransactionException("A categoria do gasto é obrigatória.");
        }
        if (date == null) {
            throw new InvalidTransactionException("A data do gasto é obrigatória.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new InvalidTransactionException("A data do gasto não pode estar no futuro.");
        }
    }
}
