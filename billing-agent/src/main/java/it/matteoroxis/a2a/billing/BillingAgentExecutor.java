package it.matteoroxis.a2a.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.matteoroxis.a2a.billing.invoice.DuplicateCharge;
import it.matteoroxis.a2a.billing.invoice.Invoice;
import it.matteoroxis.a2a.billing.invoice.InvoiceQueries;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.stereotype.Component;

/**
 * Drives the task lifecycle of the billing agent. Every branch ends in a state the
 * caller can observe: INPUT_REQUIRED when the request is ambiguous, REJECTED when
 * the handoff is incomplete, COMPLETED with an artifact otherwise.
 */
@Component
public class BillingAgentExecutor implements AgentExecutor {

    private static final Pattern INVOICE_ID = Pattern.compile("INV-\\d+");

    private final InvoiceQueries invoices;

    public BillingAgentExecutor(InvoiceQueries invoices) {
        this.invoices = invoices;
    }

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        if (context.getTask() == null) {
            // Create the task with the caller's first message in its history. A plain submit()
            // starts with an empty history, and the follow-up turn would lose the handoff.
            emitter.addTask(emitter.taskBuilder()
                    .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                    .history(List.of(context.getMessage()))
                    .build());
        }
        emitter.startWork();

        BillingRequest request = BillingRequest.from(context);
        if (request == null) {
            emitter.reject(say(emitter, "I need a customerId and a billing period to look up invoices."));
            return;
        }

        String invoiceId = findInvoiceId(context.getUserInput(" "));
        if (invoiceId == null) {
            List<Invoice> matches = invoices.findByCustomerAndPeriod(request.customerId(), request.period());
            if (matches.isEmpty()) {
                emitter.reject(say(emitter, "There are no invoices for %s in %s."
                        .formatted(request.customerId(), request.period())));
                return;
            }
            if (matches.size() > 1) {
                List<String> ids = matches.stream().map(Invoice::id).toList();
                emitter.requiresInput(say(emitter, "I found %d invoices for %s: %s. Which one should I check?"
                        .formatted(ids.size(), request.period(), String.join(", ", ids))));
                return;
            }
            invoiceId = matches.getFirst().id();
        }

        List<DuplicateCharge> duplicates = invoices.findDuplicateCharges(request.customerId(), invoiceId);
        emitter.addArtifact(
                List.of(new TextPart(explain(invoiceId, duplicates)),
                        new DataPart(resolution(invoiceId, duplicates))),
                "billing-resolution", "Duplicate charge check", null);
        emitter.complete();
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        emitter.cancel();
    }

    private static Message say(AgentEmitter emitter, String text) {
        return emitter.newAgentMessage(List.of(new TextPart(text)), null);
    }

    private static String findInvoiceId(String text) {
        Matcher matcher = INVOICE_ID.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : null;
    }

    private static String explain(String invoiceId, List<DuplicateCharge> duplicates) {
        if (duplicates.isEmpty()) {
            return "Invoice %s has no duplicate charges.".formatted(invoiceId);
        }
        StringBuilder text = new StringBuilder("Invoice %s contains duplicate charges:".formatted(invoiceId));
        for (DuplicateCharge charge : duplicates) {
            text.append(" %s billed %d times at %s (overcharge %s)."
                    .formatted(charge.sku(), charge.count(), charge.unitAmount(), charge.overcharge()));
        }
        return text.append(" A refund of %s is due.".formatted(refund(duplicates))).toString();
    }

    private static Map<String, Object> resolution(String invoiceId, List<DuplicateCharge> duplicates) {
        return Map.of(
                "invoiceId", invoiceId,
                "duplicateSkus", duplicates.stream().map(DuplicateCharge::sku).toList(),
                "refundDue", refund(duplicates).toPlainString());
    }

    private static BigDecimal refund(List<DuplicateCharge> duplicates) {
        return duplicates.stream().map(DuplicateCharge::overcharge).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
