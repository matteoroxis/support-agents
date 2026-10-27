package it.matteoroxis.a2a.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import it.matteoroxis.a2a.triage.classify.IntentClassifier;
import it.matteoroxis.a2a.triage.classify.TriageDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The offline profile starts without an LLM and still produces a usable handoff. */
@SpringBootTest
@ActiveProfiles("offline")
class TriageAgentOfflineTest {

    @Autowired
    IntentClassifier classifier;

    @Test
    void classifiesABillingRequestAndResolvesTheMonth() {
        TriageDecision decision = classifier.classify(
                "I think I was charged twice for my plan in September", LocalDate.of(2026, 10, 26));

        assertThat(decision.intent()).isEqualTo(TriageDecision.Intent.BILLING);
        assertThat(decision.period()).isEqualTo("2026-09");
    }

    @Test
    void resolvesAMonthLaterThanTodayToThePreviousYear() {
        TriageDecision decision = classifier.classify("Refund for the December invoice", LocalDate.of(2026, 10, 26));

        assertThat(decision.period()).isEqualTo("2025-12");
    }

    @Test
    void leavesNonBillingRequestsToTheRouter() {
        TriageDecision decision = classifier.classify("The app crashes on login", LocalDate.of(2026, 10, 26));

        assertThat(decision.intent()).isEqualTo(TriageDecision.Intent.OTHER);
    }
}
