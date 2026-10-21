package dio.budgeting.domain;

import java.time.LocalDate;

/**
 * Intervalo de datas com as duas pontas inclusivas. Uma ponta nula deixa o período aberto daquele lado.
 */
public record Period(LocalDate from, LocalDate to) {
    public Period {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidPeriodException("A data inicial não pode ser depois da data final.");
        }
    }

    public boolean contains(LocalDate date) {
        if (from == null && to == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }
}
