package io.talken.dex.api.dto;

import io.talken.dex.api.service.TokenMetaData;
import lombok.Data;

@Data
public class UpdateHolderResult {
	private TokenMetaData.ManagedInfo managedAccountPack;
}
