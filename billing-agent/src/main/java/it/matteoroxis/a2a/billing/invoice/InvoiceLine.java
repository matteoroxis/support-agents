package it.matteoroxis.a2a.billing.invoice;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record InvoiceLine(String sku, String description,
                          @Field(targetType = FieldType.DECIMAL128) BigDecimal amount) {
}
