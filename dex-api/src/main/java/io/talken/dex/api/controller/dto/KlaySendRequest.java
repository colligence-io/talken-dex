package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class KlaySendRequest {
    @NotEmpty
    private String symbol;
	@NotEmpty
	private String to;
	@NotNull
	private BigDecimal amount;

    private String contract;
}
