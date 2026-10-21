package dio.budgeting;

import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo com um modelo de IA real (OpenAI, ou Groq com o perfil groq): o texto vai para a IA, ela escolhe
 * a ferramenta e o resultado é conferido no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("dio.budgeting.AiKeys#available")
class AssistantFlowIT {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionEntityRepository transactionEntityRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionEntityRepository.deleteAll();
    }

    @Test
    void should_registerExpenseWithCategoryAndDate_when_commandMentionsYesterday() throws Exception {
        var answer = ask("Ontem gastei 45 reais e 90 centavos almoçando num restaurante");

        assertThat(answer).containsPattern("R\\$\\s?45");
        assertThat(transactionEntityRepository.findAll()).singleElement().satisfies(transaction -> {
            assertThat(transaction.getAmount()).isEqualTo(4590);
            assertThat(transaction.getCategory()).isEqualTo("RESTAURANT");
            assertThat(transaction.getDate()).isEqualTo(LocalDate.now().minusDays(1));
        });
    }

    @Test
    void should_onlyCountThisMonth_when_askedAboutThisMonth() throws Exception {
        var today = LocalDate.now();
        transactionEntityRepository.save(
                new TransactionEntity(UUID.randomUUID(), "Mercado", 10000, "GROCERIES", today));
        transactionEntityRepository.save(
                new TransactionEntity(UUID.randomUUID(), "Mercado do mês passado", 5000, "GROCERIES",
                        today.minusMonths(1).withDayOfMonth(1)));

        var answer = ask("Quanto eu gastei este mês?");

        assertThat(answer).containsAnyOf("100", "cem").doesNotContain("150");
    }

    @Test
    void should_notRegisterAnything_when_amountIsZero() throws Exception {
        ask("Gastei zero reais no posto de gasolina");

        assertThat(transactionEntityRepository.count()).isZero();
    }

    private String ask(String message) throws Exception {
        var answer = mockMvc.perform(post("/transactions/ai/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonMapper.builder().build().writeValueAsString(Map.of("message", message))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        System.out.println("[IA] " + message + " -> " + answer);
        return answer;
    }
}
