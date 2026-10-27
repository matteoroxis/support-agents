package it.matteoroxis.a2a.triage;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import it.matteoroxis.a2a.triage.billing.BillingAgentClient;
import it.matteoroxis.a2a.triage.billing.Handoff;
import it.matteoroxis.a2a.triage.classify.IntentClassifier;
import it.matteoroxis.a2a.triage.classify.TriageDecision;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer-facing side of the triage agent. A session that has a task waiting
 * for input is routed straight back to that task; everything else is classified first.
 */
@RestController
@RequestMapping("/support")
public class SupportController {

    public record SupportRequest(String sessionId, String customerId, String text) {
    }

    public record SupportReply(String reply, String handledBy, String taskState, String taskId, String contextId) {

        static SupportReply fromTriage(String reply) {
            return new SupportReply(reply, "triage-agent", null, null, null);
        }
    }

    private final IntentClassifier classifier;
    private final BillingAgentClient billing;
    private final Clock clock;
    private final Map<String, Handoff> waitingForInput = new ConcurrentHashMap<>();

    public SupportController(IntentClassifier classifier, BillingAgentClient billing, Clock clock) {
        this.classifier = classifier;
        this.billing = billing;
        this.clock = clock;
    }

    @PostMapping("/messages")
    public SupportReply handle(@RequestBody SupportRequest request) {
        Handoff pending = waitingForInput.remove(request.sessionId());
        if (pending != null) {
            return relay(request.sessionId(), billing.resume(pending, request.text()));
        }

        TriageDecision decision = classifier.classify(request.text(), LocalDate.now(clock));
        if (decision.intent() != TriageDecision.Intent.BILLING) {
            return SupportReply.fromTriage("This demo routes billing questions only.");
        }
        if (decision.period() == null) {
            return SupportReply.fromTriage("Which month is the charge from?");
        }
        return relay(request.sessionId(), billing.delegate(request.customerId(), decision));
    }

    private SupportReply relay(String sessionId, Task task) {
        TaskState state = task.status().state();
        if (state == TaskState.TASK_STATE_INPUT_REQUIRED) {
            waitingForInput.put(sessionId, new Handoff(task.id(), task.contextId()));
        }
        String reply = state == TaskState.TASK_STATE_COMPLETED
                ? text(task.artifacts().stream().flatMap(artifact -> artifact.parts().stream()))
                : text(Stream.ofNullable(task.status().message()).flatMap(message -> message.parts().stream()));
        return new SupportReply(reply, "billing-agent", state.name(), task.id(), task.contextId());
    }

    private static String text(Stream<Part<?>> parts) {
        List<String> texts = parts
                .filter(TextPart.class::isInstance)
                .map(part -> ((TextPart) part).text())
                .filter(Objects::nonNull)
                .toList();
        return String.join("\n", texts);
    }
}
