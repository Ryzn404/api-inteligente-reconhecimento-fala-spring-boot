package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

public record PersistTransactionInput(@ToolParam(description = "Descrição do gasto") String description,
                                      @ToolParam(description = "Valor do gasto (em centavos)") long amount,
                                      @ToolParam(description = "Categoria de uma transação") Category category,
                                      @ToolParam(required = false,
                                              description = "Data do gasto no formato AAAA-MM-DD. " +
                                                      "Deixe vazio quando a pessoa não disser a data; nesse caso, vale a data de hoje")
                                      LocalDate date) {
}
