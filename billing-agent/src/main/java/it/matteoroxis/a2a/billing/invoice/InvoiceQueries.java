package it.matteoroxis.a2a.billing.invoice;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Component;

/**
 * The billing agent's private view of its data. Nothing here is visible to the
 * caller: the triage agent only ever sees the artifact built from the results.
 */
@Component
public class InvoiceQueries {

    private final MongoTemplate mongo;

    public InvoiceQueries(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<Invoice> findByCustomerAndPeriod(String customerId, String period) {
        return mongo.find(query(where("customerId").is(customerId).and("period").is(period)), Invoice.class);
    }

    /**
     * Equivalent shell pipeline:
     * <pre>
     * db.invoices.aggregate([
     *   { $match: { _id: invoiceId, customerId: customerId } },
     *   { $unwind: "$lines" },
     *   { $group: { _id: "$lines.sku", count: { $sum: 1 },
     *               unitAmount: { $first: "$lines.amount" }, total: { $sum: "$lines.amount" } } },
     *   { $match: { count: { $gt: 1 } } },
     *   { $project: { _id: 0, sku: "$_id", count: 1, unitAmount: 1, total: 1 } }
     * ])
     * </pre>
     */
    public List<DuplicateCharge> findDuplicateCharges(String customerId, String invoiceId) {
        Aggregation pipeline = newAggregation(
                match(where("_id").is(invoiceId).and("customerId").is(customerId)),
                unwind("lines"),
                group("lines.sku")
                        .count().as("count")
                        .first("lines.amount").as("unitAmount")
                        .sum("lines.amount").as("total"),
                match(where("count").gt(1)),
                project("count", "unitAmount", "total").and("_id").as("sku").andExclude("_id"));
        return mongo.aggregate(pipeline, Invoice.class, DuplicateCharge.class).getMappedResults();
    }
}
