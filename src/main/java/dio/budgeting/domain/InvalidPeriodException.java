package dio.budgeting.domain;

public class InvalidPeriodException extends RuntimeException {
    public InvalidPeriodException(String message) {
        super(message);
    }
}
