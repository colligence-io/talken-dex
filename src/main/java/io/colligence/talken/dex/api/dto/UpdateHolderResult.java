package io.colligence.talken.dex.api.dto;

import io.colligence.talken.dex.api.service.TokenMetaData;
import lombok.Data;

@Data
public class UpdateHolderResult {
	private TokenMetaData.ManagedInfo managedAccountPack;
}
