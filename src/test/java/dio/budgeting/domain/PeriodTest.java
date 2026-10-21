package dio.budgeting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodTest {
    private static final LocalDate SEP_30 = LocalDate.of(2025, 9, 30);
    private static final LocalDate OCT_1 = LocalDate.of(2025, 10, 1);
    private static final LocalDate OCT_31 = LocalDate.of(2025, 10, 31);
    private static final LocalDate NOV_1 = LocalDate.of(2025, 11, 1);

    @Test
    void should_includeBothEnds() {
        var october = new Period(OCT_1, OCT_31);

        assertThat(october.contains(OCT_1)).isTrue();
        assertThat(october.contains(OCT_31)).isTrue();
        assertThat(october.contains(SEP_30)).isFalse();
        assertThat(october.contains(NOV_1)).isFalse();
    }

    @Test
    void should_stayOpen_when_anEndIsMissing() {
        assertThat(new Period(OCT_1, null).contains(NOV_1)).isTrue();
        assertThat(new Period(OCT_1, null).contains(SEP_30)).isFalse();
        assertThat(new Period(null, OCT_31).contains(SEP_30)).isTrue();
        assertThat(new Period(null, OCT_31).contains(NOV_1)).isFalse();
    }

    @Test
    void should_containEverything_when_thereIsNoFilter() {
        var everything = new Period(null, null);

        assertThat(everything.contains(SEP_30)).isTrue();
        assertThat(everything.contains(null)).isTrue();
    }

    @Test
    void should_ignoreUndatedTransactions_when_periodIsFiltered() {
        assertThat(new Period(OCT_1, OCT_31).contains(null)).isFalse();
    }

    @Test
    void should_rejectPeriod_when_startIsAfterEnd() {
        assertThatThrownBy(() -> new Period(OCT_31, OCT_1))
                .isInstanceOf(InvalidPeriodException.class)
                .hasMessage("A data inicial não pode ser depois da data final.");
    }
}
