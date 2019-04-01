package io.talken.dex.api.controller.dto;

import io.talken.dex.api.shared.TokenMetaData;
import lombok.Data;

@Data
public class UpdateHolderResult {
	private TokenMetaData.ManagedInfo managedAccountPack;
}
