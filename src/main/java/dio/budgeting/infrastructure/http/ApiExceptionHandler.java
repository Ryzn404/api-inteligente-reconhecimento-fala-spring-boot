package dio.budgeting.infrastructure.http;

import dio.budgeting.domain.InvalidTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidTransactionException.class)
    ProblemDetail handleInvalidTransaction(InvalidTransactionException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Transação inválida");
        return problem;
    }
}
