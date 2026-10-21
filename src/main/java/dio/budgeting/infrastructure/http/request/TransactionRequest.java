package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;

import java.time.LocalDate;

public record TransactionRequest(String description, Category category, long amount, LocalDate date) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, amount, category, date);
    }
}
