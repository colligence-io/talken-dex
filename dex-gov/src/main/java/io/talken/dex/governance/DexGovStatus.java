package io.talken.dex.governance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * The type Dex gov status.
 */
@Data
public class DexGovStatus {
    /**
     * The constant isStopped.
     */
    @JsonIgnore
	public static boolean isStopped = false;
}
