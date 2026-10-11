package mekceuaeupgrade.common.transfer;

/** MODULATE was entered but no trustworthy acknowledgement exists. Never automatically replay this delivery. */
public final class UncertainNetworkDeliveryException extends RuntimeException {
    public UncertainNetworkDeliveryException(Throwable cause) { super("AE output delivery acknowledgement is unknown", cause); }
}
