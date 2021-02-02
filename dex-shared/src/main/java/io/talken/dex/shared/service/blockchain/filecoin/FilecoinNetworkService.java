package io.talken.dex.shared.service.blockchain.filecoin;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class FilecoinNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(FilecoinNetworkService.class);

	private final DexSettings dexSettings;

	private FilecoinRpcClient client;

	@PostConstruct
	private void init() {
		DexSettings._Filecoin settings = dexSettings.getBcnode().getFilecoin();
		String infuraUri = settings.getInfuraUri();
		String projectId = settings.getProjectId();
		String projectSecret = settings.getProjectSecret();

		this.client = new FilecoinRpcClient(infuraUri, projectId, projectSecret);
		logger.info("Using Filecoin SERVICE Network : {} / {} / {}", infuraUri, projectId, projectSecret);
	}

	/**
	 * Filecoin JsonRpc client
	 *
	 * @return
	 */
	public FilecoinRpcClient getClient() {
		return this.client;
	}
}
