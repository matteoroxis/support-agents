package it.matteoroxis.a2a.billing.invoice;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Seeds placeholder invoices for the demo when the collection is empty. */
@Component
@ConditionalOnProperty(name = "billing.seed-sample-data", havingValue = "true", matchIfMissing = true)
class SampleInvoices implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleInvoices.class);

    private final MongoTemplate mongo;

    SampleInvoices(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (mongo.estimatedCount(Invoice.class) > 0) {
            return;
        }
        BigDecimal pro = new BigDecimal("49.00");
        mongo.insertAll(List.of(
                new Invoice("INV-3301", "C-1042", "2026-09", "PAID", List.of(
                        new InvoiceLine("PLAN-PRO", "Pro plan, September", pro))),
                new Invoice("INV-3317", "C-1042", "2026-09", "PAID", List.of(
                        new InvoiceLine("PLAN-PRO", "Pro plan, September", pro),
                        new InvoiceLine("ADDON-STORAGE", "Extra storage 100 GB", new BigDecimal("9.00")),
                        new InvoiceLine("PLAN-PRO", "Pro plan, September", pro))),
                new Invoice("INV-3402", "C-2077", "2026-09", "OPEN", List.of(
                        new InvoiceLine("PLAN-TEAM", "Team plan, September", new BigDecimal("199.00"))))));
        log.info("Seeded sample invoices");
    }
}
