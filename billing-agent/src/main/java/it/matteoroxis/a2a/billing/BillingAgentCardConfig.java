package it.matteoroxis.a2a.billing;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The public contract of the billing agent. spring-ai-a2a serves this bean at
 * /.well-known/agent-card.json. It says what the agent can do, never how.
 */
@Configuration
public class BillingAgentCardConfig {

    @Bean
    public AgentCard agentCard(@Value("${billing.agent.public-url}") String publicUrl) {
        return AgentCard.builder()
                .name("Billing Agent")
                .description("Explains invoices and detects duplicate charges for a known customer.")
                .version("1.0.0")
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", publicUrl)))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text/plain", "application/json"))
                .defaultOutputModes(List.of("text/plain", "application/json"))
                .skills(List.of(AgentSkill.builder()
                        .id("duplicate-charge-check")
                        .name("Duplicate charge check")
                        .description("Finds the customer's invoice for a billing period and reports charges that appear more than once.")
                        .tags(List.of("billing", "invoice", "refund"))
                        .examples(List.of("I was charged twice in September"))
                        .inputModes(List.of("application/json"))
                        .build()))
                .build();
    }
}
