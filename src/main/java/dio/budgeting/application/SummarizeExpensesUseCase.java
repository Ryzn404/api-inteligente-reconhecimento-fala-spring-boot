package dio.budgeting.application;

import dio.budgeting.application.output.ExpensesSummaryOutput;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class SummarizeExpensesUseCase {
    private final TransactionRepository transactionRepository;

    public SummarizeExpensesUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "summarize-expenses",
            description = "Resume os gastos registrados: total geral, quantidade de transações e total por categoria. " +
                    "Use para perguntas como 'quanto eu gastei?' ou 'onde estou gastando mais?'")
    public ExpensesSummaryOutput execute() {
        return ExpensesSummaryOutput.from(transactionRepository.findAll());
    }
}
