package dio.budgeting.application;

import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Chama as ferramentas do mesmo jeito que o modelo faz: pelo nome e com argumentos em JSON.
 */
class ToolCallingContractTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final ToolCallback[] tools = ToolCallbacks.from(
            new PersistTransactionUseCase(repository),
            new ListTransactionsByCategoryUseCase(repository),
            new SummarizeExpensesUseCase(repository));

    @Test
    void should_exposeUseCasesAsTools() {
        assertThat(tools)
                .extracting(tool -> tool.getToolDefinition().name())
                .containsExactlyInAnyOrder("persist-transaction", "list-transactions-by-category", "summarize-expenses");
    }

    @Test
    void should_describeOptionalDatesAndAllCategoriesToTheModel() {
        var persistSchema = JsonMapper.builder().build()
                .readTree(tool("persist-transaction").getToolDefinition().inputSchema());
        var summarizeSchema = JsonMapper.builder().build()
                .readTree(tool("summarize-expenses").getToolDefinition().inputSchema());

        assertThat(persistSchema.at("/properties/input/required").toString())
                .isEqualTo("[\"amount\",\"category\",\"description\"]");
        assertThat(persistSchema.at("/properties/input/properties/category/enum").toString())
                .isEqualTo("[\"GROCERIES\",\"PHARMA\",\"AUTO\",\"RESTAURANT\",\"OTHER\"]");
        assertThat(summarizeSchema.at("/required").toString()).isEqualTo("[]");
        assertThat(summarizeSchema.at("/properties/input/required").isMissingNode()).isTrue();
    }

    @Test
    void should_persistAndSummarizePeriod_when_modelCallsTheTools() {
        tool("persist-transaction").call("""
                {"input": {"description": "Compras no mercado", "amount": 8050, "category": "GROCERIES", "date": "2025-10-15"}}
                """);
        tool("persist-transaction").call("""
                {"input": {"description": "Pizza", "amount": 6500, "category": "RESTAURANT", "date": "2025-11-02"}}
                """);

        var october = tool("summarize-expenses").call("""
                {"input": {"from": "2025-10-01", "to": "2025-10-31"}}
                """);
        var everything = tool("summarize-expenses").call("{}");

        assertThat(october)
                .contains("\"from\":\"2025-10-01\"")
                .contains("\"category\":\"GROCERIES\"")
                .contains("\"total\":80.5")
                .doesNotContain("RESTAURANT");
        assertThat(everything).contains("\"total\":145.5");
    }

    @Test
    void should_useTodaysDate_when_modelDoesNotInformDate() {
        var saved = tool("persist-transaction").call("""
                {"input": {"description": "Remédio", "amount": 3990, "category": "PHARMA"}}
                """);

        assertThat(saved).contains("\"date\":\"" + LocalDate.now() + "\"");
    }

    @Test
    void should_sendErrorMessageBackToModel_when_transactionIsInvalid() {
        var thrown = catchThrowable(() -> tool("persist-transaction").call("""
                {"input": {"description": "Compras no mercado", "amount": 0, "category": "GROCERIES"}}
                """));

        assertThat(thrown).isInstanceOf(ToolExecutionException.class);
        // Com spring.ai.tools.throw-exception-on-error=false (padrão), este texto é devolvido ao modelo.
        assertThat(messageForModel(thrown)).contains("O valor do gasto deve ser maior que zero.");
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void should_sendErrorMessageBackToModel_when_periodIsInvalid() {
        var thrown = catchThrowable(() -> tool("summarize-expenses").call("""
                {"input": {"from": "2025-10-31", "to": "2025-10-01"}}
                """));

        assertThat(messageForModel(thrown)).contains("A data inicial não pode ser depois da data final.");
    }

    private ToolCallback tool(String name) {
        return Arrays.stream(tools)
                .filter(tool -> tool.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String messageForModel(Throwable thrown) {
        return DefaultToolExecutionExceptionProcessor.builder().build().process((ToolExecutionException) thrown);
    }
}
