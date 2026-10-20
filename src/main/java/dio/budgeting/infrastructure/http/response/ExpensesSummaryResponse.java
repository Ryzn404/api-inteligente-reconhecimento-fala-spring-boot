package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.ExpensesSummaryOutput;

import java.util.List;

public record ExpensesSummaryResponse(List<CategoryTotalResponse> categories, int transactionCount, double total) {
    public static ExpensesSummaryResponse from(ExpensesSummaryOutput output) {
        return new ExpensesSummaryResponse(
                output.categories().stream().map(CategoryTotalResponse::from).toList(),
                output.transactionCount(),
                output.total());
    }

    public record CategoryTotalResponse(String category, int transactionCount, double total) {
        static CategoryTotalResponse from(ExpensesSummaryOutput.CategoryTotal output) {
            return new CategoryTotalResponse(output.category(), output.transactionCount(), output.total());
        }
    }
}
