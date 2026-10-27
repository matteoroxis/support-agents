package it.matteoroxis.a2a.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import it.matteoroxis.a2a.billing.invoice.DuplicateCharge;
import it.matteoroxis.a2a.billing.invoice.Invoice;
import it.matteoroxis.a2a.billing.invoice.InvoiceQueries;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Exercises the billing agent through the real A2A JSON-RPC endpoint with the real
 * A2A Java client. Only the MongoDB queries are replaced, so no database is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "spring.mongodb.uri=mongodb://localhost:1/unused",
        "billing.seed-sample-data=false"
})
class BillingAgentA2ATest {

    // The Agent Card must advertise the real endpoint, because the client sends to the
    // URL it finds in the card. So the port is chosen before the context starts.
    static final int port = freePort();

    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> port);
        registry.add("billing.agent.public-url", () -> "http://localhost:" + port + "/");
    }

    @MockitoBean
    InvoiceQueries invoices;

    @Test
    void publishesAgentCardAtWellKnownPath() throws Exception {
        AgentCard card = A2ACardResolver.builder().baseUrl(baseUrl()).build().getAgentCard();

        assertThat(card.name()).isEqualTo("Billing Agent");
        assertThat(card.skills()).extracting(skill -> skill.id()).containsExactly("duplicate-charge-check");
        assertThat(card.supportedInterfaces())
                .extracting(i -> i.protocolBinding() + " " + i.protocolVersion() + " " + i.url())
                .contains("JSONRPC 1.0 " + baseUrl() + "/");
    }

    @Test
    void asksForInputThenCompletesOnTheSameTask() throws Exception {
        when(invoices.findByCustomerAndPeriod("C-1042", "2026-09")).thenReturn(List.of(
                new Invoice("INV-3301", "C-1042", "2026-09", "PAID", List.of()),
                new Invoice("INV-3317", "C-1042", "2026-09", "PAID", List.of())));
        when(invoices.findDuplicateCharges("C-1042", "INV-3317")).thenReturn(List.of(
                new DuplicateCharge("PLAN-PRO", 2, new BigDecimal("49.00"), new BigDecimal("98.00"))));
        Client client = client();

        Task first = send(client, Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(new TextPart("Customer reports a duplicate charge in September"),
                        new DataPart(Map.of("customerId", "C-1042", "intent", "billing", "period", "2026-09")))
                .build());

        assertThat(first.status().state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED);
        assertThat(text(first.status().message())).contains("INV-3301", "INV-3317");

        // The follow-up carries only text: the customerId and period come back from the task history.
        Task second = send(client, A2A.createUserTextMessage("INV-3317", first.contextId(), first.id()));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.contextId()).isEqualTo(first.contextId());
        assertThat(second.status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
        assertThat(second.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.name()).isEqualTo("Duplicate charge check");
            assertThat(artifact.parts()).filteredOn(TextPart.class::isInstance).singleElement()
                    .satisfies(part -> assertThat(((TextPart) part).text())
                            .contains("PLAN-PRO billed 2 times", "refund of 49.00"));
            assertThat(artifact.parts()).filteredOn(DataPart.class::isInstance).singleElement()
                    .satisfies(part -> assertThat((Map<Object, Object>) ((DataPart) part).data())
                            .containsEntry("invoiceId", "INV-3317")
                            .containsEntry("refundDue", "49.00"));
        });
    }

    @Test
    void rejectsAHandoffWithoutStructuredContext() throws Exception {
        Task task = send(client(), A2A.toUserMessage("Something is wrong with my bill"));

        assertThat(task.status().state()).isEqualTo(TaskState.TASK_STATE_REJECTED);
        verify(invoices, never()).findByCustomerAndPeriod(any(), any());
    }

    private Client client() throws Exception {
        AgentCard card = A2ACardResolver.builder().baseUrl(baseUrl()).build().getAgentCard();
        return Client.builder(card).withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig()).build();
    }

    private static Task send(Client client, Message message) throws Exception {
        AtomicReference<Task> result = new AtomicReference<>();
        client.sendMessage(message, List.of((event, card) -> {
            if (event instanceof TaskEvent taskEvent) {
                result.set(taskEvent.getTask());
            }
        }), null, null);
        assertThat(result.get()).as("task returned by the billing agent").isNotNull();
        return result.get();
    }

    private static String text(Message message) {
        return message.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(part -> ((TextPart) part).text())
                .reduce("", String::concat);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
