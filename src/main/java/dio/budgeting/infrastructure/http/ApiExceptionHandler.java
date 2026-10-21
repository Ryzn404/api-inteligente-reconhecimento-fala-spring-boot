package dio.budgeting.infrastructure.http;

import dio.budgeting.domain.InvalidPeriodException;
import dio.budgeting.domain.InvalidTransactionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidTransactionException.class)
    ProblemDetail handleInvalidTransaction(InvalidTransactionException exception) {
        return badRequest("Transação inválida", exception.getMessage());
    }

    @ExceptionHandler(InvalidPeriodException.class)
    ProblemDetail handleInvalidPeriod(InvalidPeriodException exception) {
        return badRequest("Período inválido", exception.getMessage());
    }

    // Valor inválido na URL, como /transactions/MERCADO ou ?from=ontem.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return badRequest("Parâmetro inválido",
                invalidValueMessage(exception.getName(), exception.getValue(), exception.getRequiredType()));
    }

    // Valor inválido no JSON, como "category": "MERCADO" ou "date": "ontem".
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof InvalidFormatException invalidFormat && !invalidFormat.getPath().isEmpty()) {
            var field = invalidFormat.getPath().getLast().getPropertyName();
            return badRequest("Requisição inválida",
                    invalidValueMessage(field, invalidFormat.getValue(), invalidFormat.getTargetType()));
        }
        return badRequest("Requisição inválida", "O corpo da requisição não é um JSON válido.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        return exception.getBody();
    }

    // A OpenAI recusou ou não completou a chamada: chave inválida, conta sem créditos, serviço fora do ar.
    @ExceptionHandler({NonTransientAiException.class, TransientAiException.class})
    ProblemDetail handleAiFailure(RuntimeException exception) {
        var error = describe(exception);
        log.warn("Falha ao chamar o serviço de IA: {}", error);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, aiFailureMessage(error));
        problem.setTitle("Falha no serviço de IA");
        return problem;
    }

    private static ProblemDetail badRequest(String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(title);
        return problem;
    }

    private static String aiFailureMessage(String error) {
        if (error.contains("insufficient_quota")) {
            return "A conta da OpenAI está sem créditos. Adicione créditos em platform.openai.com e tente de novo.";
        }
        if (error.contains("invalid_api_key")) {
            return "A chave do serviço de IA foi recusada. Confira a variável OPENAI_API_KEY (ou GROQ_API_KEY, no perfil groq).";
        }
        if (error.contains("model_not_found")) {
            return "O modelo de IA configurado não existe ou a chave não tem acesso a ele. " +
                    "Confira o nome do modelo (no perfil groq, a variável GROQ_CHAT_MODEL).";
        }
        if (error.contains("rate_limit_exceeded")) {
            return "O limite de uso do serviço de IA foi atingido. Aguarde um minuto e tente de novo.";
        }
        return "O serviço de IA não respondeu como esperado. Tente de novo em instantes.";
    }

    // A transcrição embrulha o erro da OpenAI em outra exceção; o motivo real fica nas causas.
    private static String describe(Throwable exception) {
        var messages = new StringBuilder();
        for (var current = exception; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append(System.lineSeparator());
        }
        return messages.toString();
    }

    private static String invalidValueMessage(String field, Object value, Class<?> type) {
        var message = "Valor inválido para '%s': '%s'.".formatted(field, value);
        if (type != null && type.isEnum()) {
            var accepted = Arrays.stream(type.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return message + " Use um destes: " + accepted + ".";
        }
        if (LocalDate.class.equals(type)) {
            return message + " Use uma data no formato AAAA-MM-DD.";
        }
        return message;
    }
}
