package io.talken.dex.governance.service.bctx.monitor.luniverse;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigInteger;

@Document("luniverse_block")
@Data
public class LuniverseBlockDocument {
	@Id
	private String id;

	@Indexed
	private BigInteger number;

	private EthBlock.Block data;

	public LuniverseBlockDocument() {
	}

	public LuniverseBlockDocument(EthBlock.Block block) {
		this.id = block.getHash();
		this.number = block.getNumber();
		this.data = block;
	}
}
