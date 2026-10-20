package dio.budgeting.application.output;

import java.math.BigDecimal;

final class Cents {
    private Cents() {
    }

    static double toReais(long cents) {
        return BigDecimal.valueOf(cents, 2).doubleValue();
    }
}
