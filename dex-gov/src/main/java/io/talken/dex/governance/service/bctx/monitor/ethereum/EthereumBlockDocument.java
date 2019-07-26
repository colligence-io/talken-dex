package io.talken.dex.governance.service.bctx.monitor.ethereum;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigInteger;

@Document("ethereum_block")
@Data
public class EthereumBlockDocument {
	@Id
	private String id;

	@Indexed
	private BigInteger number;

	private EthBlock.Block data;

	public static EthereumBlockDocument from(EthBlock.Block block) {
		EthereumBlockDocument rtn = new EthereumBlockDocument();
		rtn.id = block.getHash();
		rtn.number = block.getNumber();
		rtn.data = block;
		return rtn;
	}
}
