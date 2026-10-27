package it.matteoroxis.a2a.triage.classify;

import java.time.LocalDate;

public interface IntentClassifier {

    TriageDecision classify(String request, LocalDate today);
}
