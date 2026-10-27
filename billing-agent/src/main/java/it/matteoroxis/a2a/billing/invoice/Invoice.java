package it.matteoroxis.a2a.billing.invoice;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("invoices")
public record Invoice(@Id String id, String customerId, String period, String status, List<InvoiceLine> lines) {
}
