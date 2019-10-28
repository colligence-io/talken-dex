package io.talken.dex.api.service.integration;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.service.TokenMetaService;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.BlockChainPlatformNotSupportedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PrivateWalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(PrivateWalletService.class);

	@Autowired
	private TokenMetaService tmService;

	public PrivateWalletTransferDTO createTransferDTObase(PrivateWalletMsgTypeEnum msgType, String assetCode) throws TokenMetaNotFoundException, BlockChainPlatformNotSupportedException {
		TokenMetaTable.Meta tokenMeta = tmService.getTokenMeta(assetCode);

		String platform_name = tokenMeta.getPlatform();
		if(platform_name == null) throw new BlockChainPlatformNotSupportedException(assetCode);

		BlockChainPlatformEnum platform;
		try {
			platform = BlockChainPlatformEnum.valueOf(platform_name);
		} catch(IllegalArgumentException ex) {
			throw new BlockChainPlatformNotSupportedException(assetCode);
		}

		PrivateWalletTransferDTO dto = new PrivateWalletTransferDTO(msgType);
		dto.setPlatform(platform);
		dto.setWalletType(platform.getWalletType());
		dto.setSignType(platform.getWalletType().getSignType());
		dto.setSymbol(assetCode);
		if(tokenMeta.getAux() != null) {
			for(Map.Entry<TokenMetaAuxCodeEnum, Object> auxEntry : tokenMeta.getAux().entrySet()) {
				if(!auxEntry.getKey().equals(TokenMetaAuxCodeEnum.TOKEN_CARD_THEME_COLOR))
					dto.getAux().put(auxEntry.getKey().name(), auxEntry.getValue());
			}
		}
		return dto;
	}
}
