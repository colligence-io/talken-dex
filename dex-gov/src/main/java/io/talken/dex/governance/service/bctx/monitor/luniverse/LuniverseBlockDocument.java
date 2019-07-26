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

	public static LuniverseBlockDocument from(EthBlock.Block block) {
		LuniverseBlockDocument rtn = new LuniverseBlockDocument();
		rtn.id = block.getHash();
		rtn.number = block.getNumber();
		rtn.data = block;
		return rtn;
	}
}
