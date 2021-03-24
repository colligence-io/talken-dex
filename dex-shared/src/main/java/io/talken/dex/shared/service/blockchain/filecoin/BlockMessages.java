package io.talken.dex.shared.service.blockchain.filecoin;

import lombok.Data;

@Data
public class BlockMessages {
    FilecoinMessage.SecpkMessage[] secpkMessages;
    Cid[] cids;
}
