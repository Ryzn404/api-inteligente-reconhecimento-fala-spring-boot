package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class InMemoryTransactionRepository implements TransactionRepository {
    private final Map<TransactionId, Transaction> transactions = new LinkedHashMap<>();

    @Override
    public Transaction save(Transaction transaction) {
        transactions.put(transaction.getId(), transaction);
        return transaction;
    }

    @Override
    public List<Transaction> findAllByCategory(Category category) {
        return transactions.values().stream()
                .filter(transaction -> transaction.getCategory() == category)
                .toList();
    }

    @Override
    public List<Transaction> findAll() {
        return List.copyOf(transactions.values());
    }
}
