package dio.budgeting.infrastructure.http;

import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa os endpoints com banco H2 em memória e os modelos de IA simulados (sem chamar a OpenAI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerTest {
    private static final String ALL_CATEGORIES = "GROCERIES, PHARMA, AUTO, RESTAURANT, OTHER";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionEntityRepository transactionEntityRepository;

    @MockitoBean
    TranscriptionModel transcriptionModel;

    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    TextToSpeechModel textToSpeechModel;

    @BeforeEach
    void cleanDatabase() {
        transactionEntityRepository.deleteAll();
    }

    @Test
    void should_createTransaction_andReturnAmountInReais() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Compras no mercado", "category": "GROCERIES", "amount": 8050}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.amount").value(80.5))
                .andExpect(jsonPath("$.date").value(LocalDate.now().toString()));
    }

    @Test
    void should_createTransaction_withInformedDateAndNewCategory() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Jantar", "category": "RESTAURANT", "amount": 12000, "date": "2025-10-15"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("RESTAURANT"))
                .andExpect(jsonPath("$.date").value("2025-10-15"));
    }

    @Test
    void should_returnBadRequest_when_transactionIsInvalid() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Compras no mercado", "category": "GROCERIES", "amount": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Transação inválida"))
                .andExpect(jsonPath("$.detail").value("O valor do gasto deve ser maior que zero."));

        assertThat(transactionEntityRepository.count()).isZero();
    }

    @Test
    void should_explainValidCategories_when_categoryInUrlIsInvalid() throws Exception {
        mockMvc.perform(get("/transactions/GROCERIES."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parâmetro inválido"))
                .andExpect(jsonPath("$.detail").value(
                        "Valor inválido para 'category': 'GROCERIES.'. Use um destes: " + ALL_CATEGORIES + "."));
    }

    @Test
    void should_explainValidCategories_when_categoryInBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Compras", "category": "MERCADO", "amount": 1000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value(
                        "Valor inválido para 'category': 'MERCADO'. Use um destes: " + ALL_CATEGORIES + "."));
    }

    @Test
    void should_explainDateFormat_when_dateIsInvalid() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("from", "ontem"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Valor inválido para 'from': 'ontem'. Use uma data no formato AAAA-MM-DD."));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Compras", "category": "GROCERIES", "amount": 1000, "date": "ontem"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Valor inválido para 'date': 'ontem'. Use uma data no formato AAAA-MM-DD."));
    }

    @Test
    void should_listTransactionsByCategory() throws Exception {
        createTransaction("Compras no mercado", "GROCERIES", 8000);
        createTransaction("Remédio", "PHARMA", 3990);

        mockMvc.perform(get("/transactions/PHARMA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Remédio"))
                .andExpect(jsonPath("$[0].amount").value(39.9));
    }

    @Test
    void should_summarizeExpensesByCategory() throws Exception {
        createTransaction("Compras no mercado", "GROCERIES", 8000);
        createTransaction("Remédio", "PHARMA", 3990);
        createTransaction("Feira", "GROCERIES", 4050);

        mockMvc.perform(get("/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(160.4))
                .andExpect(jsonPath("$.transactionCount").value(3))
                .andExpect(jsonPath("$.categories[0].category").value("GROCERIES"))
                .andExpect(jsonPath("$.categories[0].transactionCount").value(2))
                .andExpect(jsonPath("$.categories[0].total").value(120.5))
                .andExpect(jsonPath("$.categories[1].category").value("PHARMA"))
                .andExpect(jsonPath("$.categories[1].total").value(39.9));
    }

    @Test
    void should_summarizeOnlyExpensesInsidePeriod() throws Exception {
        createTransaction("Mercado de setembro", "GROCERIES", 10000, "2025-09-30");
        createTransaction("Mercado", "GROCERIES", 8000, "2025-10-01");
        createTransaction("Pizza", "RESTAURANT", 6500, "2025-10-31");

        mockMvc.perform(get("/transactions/summary").param("from", "2025-10-01").param("to", "2025-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-10-01"))
                .andExpect(jsonPath("$.to").value("2025-10-31"))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.total").value(145.0));
    }

    @Test
    void should_returnBadRequest_when_periodIsInvalid() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("from", "2025-10-31").param("to", "2025-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Período inválido"))
                .andExpect(jsonPath("$.detail").value("A data inicial não pode ser depois da data final."));
    }

    @Test
    void should_answerWithText_when_textCommandIsSent() throws Exception {
        var answer = "Você gastou 80 reais este mês.";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(answer)))));

        mockMvc.perform(post("/transactions/ai/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Quanto eu gastei este mês?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(answer));

        var prompt = capturePrompt();
        assertThat(prompt.getUserMessage().getText()).isEqualTo("Quanto eu gastei este mês?");
        assertThat(prompt.getSystemMessage().getText())
                .contains("Hoje é ")
                .contains(LocalDate.now().toString())
                .contains("R$45 (quarenta e cinco reais)")
                .doesNotContain("{answerRules}");
        verifyNoInteractions(transcriptionModel, textToSpeechModel);
    }

    @Test
    void should_explainAiFailure_when_openAiAccountHasNoCredits() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("""
                HTTP 429 - {"error": {"message": "You have no credits remaining.", "type": "insufficient_quota"}}"""));

        mockMvc.perform(post("/transactions/ai/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Quanto eu gastei este mês?"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Falha no serviço de IA"))
                .andExpect(jsonPath("$.detail").value(
                        "A conta da OpenAI está sem créditos. Adicione créditos em platform.openai.com e tente de novo."));
    }

    @Test
    void should_explainAiFailure_when_transcriptionFailsBecauseKeyIsInvalid() throws Exception {
        var openAiError = new RuntimeException("""
                401 Unauthorized: {"error": {"message": "Incorrect API key provided", "code": "invalid_api_key"}}""");
        when(transcriptionModel.transcribe(any(Resource.class)))
                .thenThrow(new NonTransientAiException("Error calling OpenAI transcription API", openAiError));
        var audio = new MockMultipartFile("file", "recording-1.m4a", "audio/mp4", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/transactions/ai").file(audio))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "A chave do serviço de IA foi recusada. Confira a variável OPENAI_API_KEY (ou GROQ_API_KEY, no perfil groq)."));
    }

    @Test
    void should_explainAiFailure_when_modelDoesNotExist() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("""
                HTTP 404 - {"error": {"message": "The model `llama-3.3-70b-versatile` does not exist", "code": "model_not_found"}}"""));

        mockMvc.perform(post("/transactions/ai/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Quanto eu gastei?"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "O modelo de IA configurado não existe ou a chave não tem acesso a ele. " +
                                "Confira o nome do modelo (no perfil groq, a variável GROQ_CHAT_MODEL)."));
    }

    @Test
    void should_explainAiFailure_when_rateLimitIsReached() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("""
                HTTP 429 - {"error": {"message": "Rate limit reached", "code": "rate_limit_exceeded"}}"""));

        mockMvc.perform(post("/transactions/ai/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Quanto eu gastei?"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "O limite de uso do serviço de IA foi atingido. Aguarde um minuto e tente de novo."));
    }

    @Test
    void should_returnBadRequest_when_textCommandIsBlank() throws Exception {
        mockMvc.perform(post("/transactions/ai/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Envie o comando no campo 'message'."));

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void should_answerWithAudio_when_voiceCommandIsSent() throws Exception {
        var audio = new MockMultipartFile("file", "recording-1.m4a", "audio/mp4",
                new ClassPathResource("audio/recording-1.m4a").getContentAsByteArray());
        var answer = "Registrei 80 reais em mercado.";
        when(transcriptionModel.transcribe(any(Resource.class))).thenReturn("Gastei 80 reais no mercado");
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(answer)))));
        when(textToSpeechModel.call(answer)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/transactions/ai").file(audio))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mp3"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        var prompt = capturePrompt();
        assertThat(prompt.getUserMessage().getText()).isEqualTo("Gastei 80 reais no mercado");
        assertThat(prompt.getSystemMessage().getText())
                .contains("assistente financeiro")
                .contains(LocalDate.now().toString())
                .contains("Fale valores de forma natural")
                .doesNotContain("R$45 (quarenta e cinco reais)");
        assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks())
                .extracting(tool -> tool.getToolDefinition().name())
                .containsExactlyInAnyOrder("persist-transaction", "list-transactions-by-category", "summarize-expenses");
    }

    @Test
    void should_returnBadRequest_when_audioIsEmpty() throws Exception {
        var emptyAudio = new MockMultipartFile("file", "vazio.m4a", "audio/mp4", new byte[0]);

        mockMvc.perform(multipart("/transactions/ai").file(emptyAudio))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Envie um arquivo de áudio no campo 'file'."));

        verifyNoInteractions(transcriptionModel, textToSpeechModel);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private Prompt capturePrompt() {
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        return prompt.getValue();
    }

    private void createTransaction(String description, String category, long amount) throws Exception {
        createTransaction(description, category, amount, LocalDate.now().toString());
    }

    private void createTransaction(String description, String category, long amount, String date) throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "%s", "category": "%s", "amount": %d, "date": "%s"}
                                """.formatted(description, category, amount, date)))
                .andExpect(status().isCreated());
    }
}
