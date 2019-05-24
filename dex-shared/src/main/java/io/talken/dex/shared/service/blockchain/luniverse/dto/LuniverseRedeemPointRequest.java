package io.talken.dex.shared.service.blockchain.luniverse.dto;

import lombok.Data;

@Data
public class LuniverseRedeemPointRequest<F> {
	private F from;
	private Inputs inputs = new Inputs();


	public void setAmount(String amount) {
		this.inputs.valueAmount = amount;
	}

	@Data
	public static class Inputs {
		private String valueAmount;
	}
}
