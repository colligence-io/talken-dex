package io.talken.dex.api;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.service.AssetConvertService;
import io.talken.dex.api.service.TokenMetaService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = ApiLauncher.class)
@AutoConfigureMockMvc
@ActiveProfiles({"common", "local"})
public class TestAssetConvertService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TestAssetConvertService.class);

	@Autowired
	private TokenMetaService tmService;

	@Autowired
	private AssetConvertService acService;

	@Test
	public void testConvert() throws Exception {
		BigDecimal convert = acService.convert("BTC", 0.001, "ETH");
		logger.info("{} {}", 0.001, convert);
	}
}
