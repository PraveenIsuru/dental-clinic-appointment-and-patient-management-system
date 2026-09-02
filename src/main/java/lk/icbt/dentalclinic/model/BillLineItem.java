package lk.icbt.dentalclinic.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One priced line on a bill.
 *
 * <p>COMPOSITION: a line item has no meaning apart from its bill and is deleted with
 * it ({@code ON DELETE CASCADE} on {@code bill_line_items}). That is the filled
 * diamond in the class diagram.
 */
public record BillLineItem(int lineId, String description, int quantity,
                           BigDecimal unitPrice, BigDecimal lineTotal) {

    /** Creates an unsaved line, computing the total rather than trusting a caller's arithmetic. */
    public static BillLineItem of(String description, int quantity, BigDecimal unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, was " + quantity);
        }
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        return new BillLineItem(0, description, quantity, unitPrice, total);
    }
}
