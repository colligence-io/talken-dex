package io.talken.dex.shared.service.blockchain.luniverse.dto;

import lombok.Data;

@Data
public abstract class LuniverseSendPointRequest<F, T> {
	private F from;
	private Inputs<T> inputs = new Inputs<>();

	public void setTo(T to) {
		this.inputs.receiverAddress = to;
	}

	public void setAmount(String amount) {
		this.inputs.valueAmount = amount;
	}

	@Data
	public static class Inputs<T> {
		private T receiverAddress;
		private String valueAmount;
	}
}
