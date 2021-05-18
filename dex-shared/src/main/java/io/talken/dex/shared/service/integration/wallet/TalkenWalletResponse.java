package io.talken.dex.shared.service.integration.wallet;

import lombok.Data;

/**
 * The type Talken wallet response.
 */
@Data
public class TalkenWalletResponse {
	private String code;
	private String message;
	private String name;
}
