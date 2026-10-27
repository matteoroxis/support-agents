package it.matteoroxis.a2a.triage.classify;

import java.time.LocalDate;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Classifies the request with an LLM and maps the answer onto {@link TriageDecision}. */
@Component
@ConditionalOnProperty(name = "triage.classifier", havingValue = "llm", matchIfMissing = true)
class LlmIntentClassifier implements IntentClassifier {

    private static final String SYSTEM_PROMPT = """
            You route customer support requests.
            Set intent to BILLING when the request is about invoices, charges, payments or refunds, otherwise OTHER.
            Set period to the billing month the customer refers to, formatted as YYYY-MM. Resolve relative \
            references against today's date. Set it to null when the customer mentions no month.
            Set summary to one sentence that states the customer's problem for the specialist who will handle it.
            """;

    private final ChatClient chat;

    LlmIntentClassifier(ChatClient.Builder builder) {
        this.chat = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    @Override
    public TriageDecision classify(String request, LocalDate today) {
        return chat.prompt()
                .user(user -> user.text("Today is {today}.\nCustomer request: {request}")
                        .param("today", today.toString())
                        .param("request", request))
                .call()
                .entity(TriageDecision.class);
    }
}
