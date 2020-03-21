package io.talken.dex.governance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class DexGovStatus {
	@JsonIgnore
	public static boolean isStopped = false;
}
