package io.talken.dex.shared.service.blockchain.filecoin;

import lombok.Data;

import java.util.List;

/**
 * The type Tip set.
 */
@Data
public class TipSet {
    private Cid[] cids;
    private List<FilecoinMessage.Block> blocks;
    private String height;
}
