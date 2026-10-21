package dio.budgeting.application.output;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Period;
import dio.budgeting.domain.Transaction;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

public record ExpensesSummaryOutput(LocalDate from,
                                    LocalDate to,
                                    List<CategoryTotal> categories,
                                    int transactionCount,
                                    double total) {
    public static ExpensesSummaryOutput from(List<Transaction> transactions, Period period) {
        var categories = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory,
                        () -> new EnumMap<>(Category.class),
                        Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> CategoryTotal.from(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(CategoryTotal::total).reversed())
                .toList();

        return new ExpensesSummaryOutput(period.from(), period.to(), categories, transactions.size(),
                Cents.toReais(sumAmounts(transactions)));
    }

    private static long sumAmounts(List<Transaction> transactions) {
        return transactions.stream().mapToLong(Transaction::getAmount).sum();
    }

    public record CategoryTotal(String category, int transactionCount, double total) {
        static CategoryTotal from(Category category, List<Transaction> transactions) {
            return new CategoryTotal(category.name(), transactions.size(), Cents.toReais(sumAmounts(transactions)));
        }
    }
}
