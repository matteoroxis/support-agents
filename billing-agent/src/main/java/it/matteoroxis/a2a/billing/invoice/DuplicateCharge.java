package it.matteoroxis.a2a.billing.invoice;

import java.math.BigDecimal;

/** One SKU billed more than once on the same invoice. */
public record DuplicateCharge(String sku, int count, BigDecimal unitAmount, BigDecimal total) {

    /** Every occurrence after the first is an overcharge. */
    public BigDecimal overcharge() {
        return unitAmount.multiply(BigDecimal.valueOf(count - 1L));
    }
}
