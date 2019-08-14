package io.talken.dex.governance.service;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.rest.RestApiClient;
import io.talken.common.util.integration.slack.SlackMessagePrefix;
import io.talken.dex.governance.GovSettings;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;

@Service
@Scope("singleton")
public class AdminAlarmService {
	@Autowired
	private GovSettings govSettings;

	@PostConstruct
	private void init() {
		ring(SlackMessagePrefix.INFO, "Talken DEX Governance Server Connected : {}", UTCUtil.getNow());
	}

	public void exception(PrefixedLogger logger, Throwable ex) {
		logger.exception(ex);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printStream = new PrintStream(out);
		ex.printStackTrace(printStream);

		ring(SlackMessagePrefix.EXCEPTION, "{}\n\nStackTrace : \n{}", ex.getMessage(), out.toString());
	}

	public void info(PrefixedLogger logger, String format, Object... args) {
		logger.info(format, args);
		ring(SlackMessagePrefix.INFO, format, args);
	}

	public void error(PrefixedLogger logger, String format, Object... args) {
		logger.error(format, args);
		ring(SlackMessagePrefix.ERROR, format, args);
	}

	public void warn(PrefixedLogger logger, String format, Object... args) {
		logger.warn(format, args);
		ring(SlackMessagePrefix.WARNING, format, args);
	}

	private void ring(String prefix, String format, Object... args) {
		String message = MessageFormatter.arrayFormat(format, args).getMessage();
		RestApiClient.requestPost(govSettings.getIntegration().getSlack().getAlarmWebHook(), Collections.singletonMap("text", prefix + " " + message), SlackResponse.class);
	}
}
