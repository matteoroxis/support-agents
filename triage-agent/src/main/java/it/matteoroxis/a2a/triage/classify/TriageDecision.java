package it.matteoroxis.a2a.triage.classify;

/**
 * What the router decided about a request.
 *
 * @param intent  where the request should go
 * @param period  billing period as YYYY-MM, or null when the customer did not mention one
 * @param summary one sentence for the specialist, written by the router
 */
public record TriageDecision(Intent intent, String period, String summary) {

    public enum Intent { BILLING, OTHER }
}
