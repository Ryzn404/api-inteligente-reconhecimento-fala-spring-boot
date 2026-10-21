package dio.budgeting.application.input;

import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

// As datas ficam em um record porque o Spring AI 2.0.0-M4 não converte LocalDate recebido direto como parâmetro.
public record SummarizeExpensesInput(@ToolParam(required = false,
                                             description = "Data inicial do período, no formato AAAA-MM-DD (inclusiva). " +
                                                     "Deixe vazio para não limitar o início")
                                     LocalDate from,
                                     @ToolParam(required = false,
                                             description = "Data final do período, no formato AAAA-MM-DD (inclusiva). " +
                                                     "Deixe vazio para não limitar o fim")
                                     LocalDate to) {
}
