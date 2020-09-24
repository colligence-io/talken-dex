package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.enums.IndexedEnumInterface;
import io.talken.common.persistence.jooq.tables.pojos.StakingEvent;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StakingEventDTO {
    public StakingEvent stakingEvent;

    public StakingStateEnum stakingState;

    public BigDecimal totalUserAmount;
    public BigDecimal amountRate;

    public int totalUserCount;
    public BigDecimal userRate;

    public enum StakingStateEnum implements IndexedEnumInterface {
        PREFARE(1),
        OPEN(2),
        CLOSE(3),
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
