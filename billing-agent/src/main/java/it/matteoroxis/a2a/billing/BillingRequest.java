package it.matteoroxis.a2a.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;

/**
 * The structured context the caller handed over in a {@code data} part.
 * On a follow-up turn the current message carries only text, so the handoff
 * is recovered from the task history that the A2A server keeps for us.
 */
record BillingRequest(String customerId, String period) {

    static BillingRequest from(RequestContext context) {
        List<Message> candidates = new ArrayList<>();
        if (context.getMessage() != null) {
            candidates.add(context.getMessage());
        }
        if (context.getTask() != null && context.getTask().history() != null) {
            candidates.addAll(context.getTask().history().reversed());
        }
        for (Message message : candidates) {
            if (message.role() != Message.Role.ROLE_USER) {
                continue;
            }
            for (Part<?> part : message.parts()) {
                if (part instanceof DataPart data && data.data() instanceof Map<?, ?> fields
                        && fields.get("customerId") instanceof String customerId
                        && fields.get("period") instanceof String period) {
                    return new BillingRequest(customerId, period);
                }
            }
        }
        return null;
    }
}
