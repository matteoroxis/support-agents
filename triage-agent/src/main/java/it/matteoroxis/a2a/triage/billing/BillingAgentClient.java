package it.matteoroxis.a2a.triage.billing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import it.matteoroxis.a2a.triage.classify.TriageDecision;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The only place where the triage agent talks to the billing agent. It knows the
 * billing agent's URL and nothing else: skills and endpoint come from the Agent Card.
 */
@Component
public class BillingAgentClient {

    private static final Logger log = LoggerFactory.getLogger(BillingAgentClient.class);

    private final String agentUrl;
    private volatile Client client;

    public BillingAgentClient(@Value("${triage.billing-agent-url}") String agentUrl) {
        this.agentUrl = agentUrl;
    }

    /** First turn: free text for the specialist plus the structured handoff. */
    public Task delegate(String customerId, TriageDecision decision) {
        Message handoff = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(new TextPart(decision.summary()),
                        new DataPart(Map.of(
                                "customerId", customerId,
                                "intent", "billing",
                                "period", decision.period())))
                .build();
        return send(handoff);
    }

    /** Follow-up turn on a task that is waiting for input: same taskId, same contextId. */
    public Task resume(Handoff handoff, String answer) {
        return send(A2A.createUserTextMessage(answer, handoff.contextId(), handoff.taskId()));
    }

    private Task send(Message message) {
        AtomicReference<Task> result = new AtomicReference<>();
        List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of((event, card) -> {
            if (event instanceof TaskEvent taskEvent) {
                result.set(taskEvent.getTask());
            }
        });
        try {
            // The agent card declares streaming=false, so this call blocks until the task
            // is terminal or interrupted and the consumer runs before it returns.
            client().sendMessage(message, consumers, null, null);
        } catch (A2AClientException e) {
            throw new IllegalStateException("Billing agent call failed", e);
        }
        Task task = result.get();
        if (task == null) {
            throw new IllegalStateException("Billing agent answered without a task");
        }
        log.info("Billing agent task {} (context {}) is {}", task.id(), task.contextId(), task.status().state());
        return task;
    }

    private Client client() throws A2AClientException {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    AgentCard card = A2ACardResolver.builder().baseUrl(agentUrl).build().getAgentCard();
                    log.info("Discovered '{}' v{} with skills {}", card.name(), card.version(),
                            card.skills().stream().map(skill -> skill.id()).toList());
                    client = Client.builder(card)
                            .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                            .build();
                }
            }
        }
        return client;
    }
}
