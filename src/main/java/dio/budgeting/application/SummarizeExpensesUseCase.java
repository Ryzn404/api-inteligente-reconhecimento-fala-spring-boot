package dio.budgeting.application;

import dio.budgeting.application.input.SummarizeExpensesInput;
import dio.budgeting.application.output.ExpensesSummaryOutput;
import dio.budgeting.domain.Period;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class SummarizeExpensesUseCase {
    private final TransactionRepository transactionRepository;

    public SummarizeExpensesUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "summarize-expenses",
            description = "Resume os gastos registrados: total geral, quantidade de transações e total por categoria. " +
                    "Use para perguntas como 'quanto eu gastei?', 'quanto gastei este mês?' ou 'onde estou gastando mais?'")
    public ExpensesSummaryOutput execute(
            @ToolParam(required = false, description = "Período da consulta. Deixe vazio para considerar todos os gastos")
            SummarizeExpensesInput input) {
        var period = input == null ? new Period(null, null) : new Period(input.from(), input.to());
        var transactions = transactionRepository.findAll().stream()
                .filter(transaction -> period.contains(transaction.getDate()))
                .toList();

        return ExpensesSummaryOutput.from(transactions, period);
    }
}
