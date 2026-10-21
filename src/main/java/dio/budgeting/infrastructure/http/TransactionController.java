package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.SummarizeExpensesUseCase;
import dio.budgeting.application.input.SummarizeExpensesInput;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.http.request.AssistantRequest;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.AssistantResponse;
import dio.budgeting.infrastructure.http.response.ExpensesSummaryResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final SummarizeExpensesUseCase summarizeExpensesUseCase;

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private final TextToSpeechModel textToSpeechModel;

    // Regras de formato da resposta: no texto "R$45 (quarenta e cinco reais)"; na voz, só por extenso.
    private final String textAnswerRules;
    private final String voiceAnswerRules;

    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                 SummarizeExpensesUseCase summarizeExpensesUseCase,
                                 TranscriptionModel transcriptionModel,
                                 @Value("classpath:prompts/system-message.st") Resource systemPrompt,
                                 @Value("classpath:prompts/answer-rules-text.st") Resource textAnswerRules,
                                 @Value("classpath:prompts/answer-rules-voice.st") Resource voiceAnswerRules,
                                 ChatClient.Builder chatClientBuilder,
                                 TextToSpeechModel textToSpeechModel) throws IOException {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.summarizeExpensesUseCase = summarizeExpensesUseCase;
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultTools(persistTransactionUseCase, listTransactionsByCategoryUseCase, summarizeExpensesUseCase)
                .build();
        this.textToSpeechModel = textToSpeechModel;
        this.textAnswerRules = textAnswerRules.getContentAsString(StandardCharsets.UTF_8).strip();
        this.voiceAnswerRules = voiceAnswerRules.getContentAsString(StandardCharsets.UTF_8).strip();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody TransactionRequest request) {
        var transaction = persistTransactionUseCase.execute(request.toInput());
        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    public List<TransactionResponse> readTransactions(@PathVariable Category category) {
        return listTransactionsByCategoryUseCase.execute(category).stream().map(TransactionResponse::from).toList();
    }

    @GetMapping("/summary")
    public ExpensesSummaryResponse summarizeExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ExpensesSummaryResponse.from(summarizeExpensesUseCase.execute(new SummarizeExpensesInput(from, to)));
    }

    @PostMapping(value = "/ai/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AssistantResponse chat(@RequestBody AssistantRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie o comando no campo 'message'.");
        }

        return new AssistantResponse(ask(request.message(), textAnswerRules));
    }

    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mp3")
    ResponseEntity<Resource> transcribe(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um arquivo de áudio no campo 'file'.");
        }

        var userMessage = transcriptionModel.transcribe(file.getResource());
        var result = ask(userMessage, voiceAnswerRules);

        byte[] audio = textToSpeechModel.call(result);
        var resource = new ByteArrayResource(audio);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("audio.mp3")
                                .build()
                                .toString())
                .body(resource);
    }

    // O modelo não sabe a data de hoje; ela vai no prompt de sistema para entender "ontem" ou "este mês".
    private String ask(String message, String answerRules) {
        var today = LocalDate.now();
        return chatClient.prompt()
                .system(system -> system
                        .param("today", today)
                        .param("weekday", today.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR))
                        .param("answerRules", answerRules))
                .user(message)
                .call()
                .content();
    }
}
