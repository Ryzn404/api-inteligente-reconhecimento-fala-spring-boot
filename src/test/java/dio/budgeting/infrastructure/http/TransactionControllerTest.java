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
                .andExpect(jsonPath("$.amount").value(80.5));
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

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getText()).isEqualTo("Gastei 80 reais no mercado");
        assertThat(prompt.getValue().getSystemMessage().getText()).contains("assistente financeiro");
        assertThat(((ToolCallingChatOptions) prompt.getValue().getOptions()).getToolCallbacks())
                .extracting(tool -> tool.getToolDefinition().name())
                .containsExactlyInAnyOrder("persist-transaction", "list-transactions-by-category", "summarize-expenses");
    }

    @Test
    void should_returnBadRequest_when_audioIsEmpty() throws Exception {
        var emptyAudio = new MockMultipartFile("file", "vazio.m4a", "audio/mp4", new byte[0]);

        mockMvc.perform(multipart("/transactions/ai").file(emptyAudio))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transcriptionModel, textToSpeechModel);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private void createTransaction(String description, String category, long amount) throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "%s", "category": "%s", "amount": %d}
                                """.formatted(description, category, amount)))
                .andExpect(status().isCreated());
    }
}
