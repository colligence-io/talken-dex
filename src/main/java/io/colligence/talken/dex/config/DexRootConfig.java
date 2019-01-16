package io.colligence.talken.dex.config;

import io.colligence.talken.common.CommonConsts;
import io.colligence.talken.common.util.PostLaunchExecutor;
import io.colligence.talken.dex.service.MessageService;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
@ComponentScan("io.colligence.talken.dex")
public class DexRootConfig {

	// PostLauncherExecutor
	@Bean
	public PostLaunchExecutor postLaunchExecutor() {
		return new PostLaunchExecutor();
	}

	// Message Source & Service
	@Bean
	public MessageSource messageSource() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasenames("i18n/message", "i18n/dexException");
		messageSource.setDefaultEncoding(CommonConsts.CHARSET_NAME);

		return messageSource;
	}

	@Bean
	public MessageService messageService() {
		return new MessageService(CommonConsts.LOCALE, messageSource());
	}
}
