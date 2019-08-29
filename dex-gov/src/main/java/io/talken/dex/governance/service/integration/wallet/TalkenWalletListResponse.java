package io.talken.dex.governance.service.integration.wallet;

import io.talken.common.util.integration.rest.RestApiResponseInterface;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;


@Data
@EqualsAndHashCode(callSuper = true)
public class TalkenWalletListResponse extends ArrayList<TalkenWalletListResponse.Wallet> implements RestApiResponseInterface {
	private String code;
	private String message;

	@Override
	public boolean checkHttpResponse(int httpStatus) {
		return RestApiResponseInterface.standardHttpSuccessCheck(httpStatus);
	}

	@Override
	public boolean checkResult() {
		return true;
	}

	@Override
	public String resultCode() {
		return code;
	}

	@Override
	public String resultMessage() {
		return message;
	}

	@Data
	public static class Wallet {
		private String id;
		private String name;
		private String coin;
		private String network;
		private String address;
		private String symbol;
		private String icon;
		private String type;
		private String contract;
		private String issuer;
		private Integer order;
		private Boolean trustline;
		private Boolean activate;
		private BigDecimal balance;
	}
}
