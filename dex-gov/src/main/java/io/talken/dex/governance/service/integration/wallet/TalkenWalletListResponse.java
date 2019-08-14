package io.talken.dex.governance.service.integration.wallet;

import io.talken.common.util.integration.rest.RestApiResponseInterface;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;


@Data
@EqualsAndHashCode(callSuper = true)
public class TalkenWalletListResponse extends ArrayList<TalkenWalletListResponse.Wallet> implements RestApiResponseInterface {
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
		return "OK";
	}

	@Override
	public String resultMessage() {
		return "OK";
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
