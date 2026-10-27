package it.matteoroxis.a2a.triage.classify;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Pattern;

import it.matteoroxis.a2a.triage.classify.TriageDecision.Intent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for the LLM classifier, so the demo runs without an API key.
 * Enabled with {@code triage.classifier=keyword} (the {@code offline} profile).
 */
@Component
@ConditionalOnProperty(name = "triage.classifier", havingValue = "keyword")
class KeywordIntentClassifier implements IntentClassifier {

    private static final Pattern BILLING = Pattern.compile(
            "charge|invoice|bill|refund|payment|paid", Pattern.CASE_INSENSITIVE);

    @Override
    public TriageDecision classify(String request, LocalDate today) {
        Intent intent = BILLING.matcher(request).find() ? Intent.BILLING : Intent.OTHER;
        return new TriageDecision(intent, period(request, today), request);
    }

    /** The most recent occurrence of the first month named in the request. */
    private static String period(String request, LocalDate today) {
        String text = request.toLowerCase(Locale.ROOT);
        for (Month month : Month.values()) {
            if (text.contains(month.name().toLowerCase(Locale.ROOT))) {
                int year = month.getValue() > today.getMonthValue() ? today.getYear() - 1 : today.getYear();
                return YearMonth.of(year, month).toString();
            }
        }
        return null;
    }
}
