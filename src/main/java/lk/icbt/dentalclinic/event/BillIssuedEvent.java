package lk.icbt.dentalclinic.event;

import lk.icbt.dentalclinic.model.Bill;

import java.time.Instant;

/** Published after a bill has been committed, so the patient can be sent their receipt. */
public record BillIssuedEvent(Bill bill, String issuedByUsername, Instant occurredAt)
        implements DomainEvent {

    public static BillIssuedEvent of(Bill bill, String issuedBy) {
        return new BillIssuedEvent(bill, issuedBy, Instant.now());
    }

    @Override
    public String summary() {
        return "Bill " + bill.getBillNo() + " issued for " + bill.getTotalAmount();
    }
}
