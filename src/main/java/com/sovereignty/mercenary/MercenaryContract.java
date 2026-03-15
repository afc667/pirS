package com.sovereignty.mercenary;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an active mercenary contract between a Province and a solo player.
 *
 * <p>For the duration of the contract, the Mercenary is treated as a temporary
 * "Citizen" of the hiring Province: they can walk through doors, open non-secure
 * chests, and do not trigger "Border Crossed" alerts or Dynmap enemy pings in
 * allied territory. They also receive friendly-fire protection.
 */
public final class MercenaryContract {

    private final UUID mercenaryUuid;
    private final long provinceId;
    private final double paymentAmount;
    private final Instant startTime;
    private final Instant expiryTime;

    /**
     * Constructs a mercenary contract.
     *
     * @param mercenaryUuid the UUID of the hired mercenary
     * @param provinceId    the hiring province's database ID
     * @param paymentAmount the Vault payment amount
     * @param startTime     when the contract was accepted
     * @param expiryTime    when the contract expires
     */
    public MercenaryContract(UUID mercenaryUuid, long provinceId, double paymentAmount,
                             Instant startTime, Instant expiryTime) {
        this.mercenaryUuid = mercenaryUuid;
        this.provinceId = provinceId;
        this.paymentAmount = paymentAmount;
        this.startTime = startTime;
        this.expiryTime = expiryTime;
    }

    /** Returns the mercenary player's UUID. */
    public UUID getMercenaryUuid() { return mercenaryUuid; }

    /** Returns the hiring province's database ID. */
    public long getProvinceId() { return provinceId; }

    /** Returns the Vault payment amount. */
    public double getPaymentAmount() { return paymentAmount; }

    /** Returns the contract start time. */
    public Instant getStartTime() { return startTime; }

    /** Returns the contract expiry time. */
    public Instant getExpiryTime() { return expiryTime; }

    /**
     * Checks whether this contract has expired.
     *
     * @return {@code true} if the current time is past the expiry time
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiryTime);
    }
}
