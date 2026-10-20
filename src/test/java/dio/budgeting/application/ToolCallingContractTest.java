package dio.budgeting.application;

import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;

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
    void should_persistAndSummarize_when_modelCallsTheTools() {
        tool("persist-transaction").call("""
                {"input": {"description": "Compras no mercado", "amount": 8050, "category": "GROCERIES"}}
                """);

        var summary = tool("summarize-expenses").call("{}");

        assertThat(summary)
                .contains("\"category\":\"GROCERIES\"")
                .contains("\"total\":80.5");
    }

    @Test
    void should_sendErrorMessageBackToModel_when_transactionIsInvalid() {
        var thrown = catchThrowable(() -> tool("persist-transaction").call("""
                {"input": {"description": "Compras no mercado", "amount": 0, "category": "GROCERIES"}}
                """));

        assertThat(thrown).isInstanceOf(ToolExecutionException.class);
        // Com spring.ai.tools.throw-exception-on-error=false (padrão), este texto é devolvido ao modelo.
        var messageForModel = DefaultToolExecutionExceptionProcessor.builder().build()
                .process((ToolExecutionException) thrown);
        assertThat(messageForModel).contains("O valor do gasto deve ser maior que zero.");
        assertThat(repository.findAll()).isEmpty();
    }

    private ToolCallback tool(String name) {
        return Arrays.stream(tools)
                .filter(tool -> tool.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
