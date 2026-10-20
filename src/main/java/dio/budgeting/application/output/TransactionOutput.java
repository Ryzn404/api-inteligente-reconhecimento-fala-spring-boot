package dio.budgeting.application.output;

import dio.budgeting.domain.Transaction;

public record TransactionOutput(String id, String description, String category, double value) {
    public static TransactionOutput from(Transaction transaction) {
        return new TransactionOutput(
                transaction.getId().uuid().toString(),
                transaction.getDescription(),
                transaction.getCategory().name(),
                Cents.toReais(transaction.getAmount()));
    }
}
