package it.matteoroxis.a2a.triage.billing;

/** A delegated task that is waiting for the customer's answer. */
public record Handoff(String taskId, String contextId) {
}
