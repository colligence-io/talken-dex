package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.enums.IndexedEnumInterface;
import io.talken.common.persistence.jooq.tables.pojos.StakingEvent;
import lombok.Data;

import java.math.BigDecimal;

/**
 * The type Staking event request.
 */
@Data
public class StakingEventRequest {
    /**
     * The Staking event.
     */
    public StakingEvent stakingEvent;

    /**
     * The Staking state.
     */
    public StakingStateEnum stakingState;

    /**
     * The Total user amount.
     */
    public BigDecimal totalUserAmount;
    /**
     * The Amount rate.
     */
    public BigDecimal amountRate;

    /**
     * The Total user count.
     */
    public int totalUserCount;
    /**
     * The User rate.
     */
    public BigDecimal userRate;

    /**
     * The enum Staking state enum.
     */
    public enum StakingStateEnum implements IndexedEnumInterface {
        /**
         * Prefare staking state enum.
         */
        PREFARE(1),
        /**
         * Open staking state enum.
         */
        OPEN(2),
        /**
         * Close staking state enum.
         */
        CLOSE(3),
        /**
         * Complete staking state enum.
         */
        COMPLETE(4);

        private final int index;

        StakingStateEnum(int index) {
            this.index = index;
        }

        @Override
        public int getIndex() {
            return index;
        }
    }
}
