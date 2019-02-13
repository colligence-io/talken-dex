package io.colligence.talken.dex.api.dto;

import io.colligence.talken.dex.api.service.ManagedAccountPack;
import lombok.Data;

@Data
public class UpdateHolderResult {
	private ManagedAccountPack managedAccountPack;
}
