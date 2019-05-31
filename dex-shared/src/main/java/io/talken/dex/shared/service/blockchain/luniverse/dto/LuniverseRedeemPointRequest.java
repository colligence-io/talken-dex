package io.talken.dex.shared.service.blockchain.luniverse.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class LuniverseRedeemPointRequest<F> {
	private F from;
	@Setter(AccessLevel.NONE)
	private Inputs inputs = new Inputs();


	public void setAmount(String amount) {
		this.inputs.valueAmount = amount;
	}

	@Data
	public static class Inputs {
		private String valueAmount;
	}
}
