package io.talken.dex.shared.service.blockchain.filecoin;

import lombok.Data;

/**
 * The type Block messages.
 */
@Data
public class BlockMessages {
    /**
     * The Secpk messages.
     */
    FilecoinMessage.SecpkMessage[] secpkMessages;
    /**
     * The Cids.
     */
    Cid[] cids;
}
