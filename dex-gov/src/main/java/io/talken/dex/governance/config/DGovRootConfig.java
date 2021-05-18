package io.talken.dex.governance.config;

import io.talken.common.CommonConsts;
import io.talken.common.service.MessageService;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PostLaunchExecutor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * The type D gov root config.
 */
@Configuration
@ComponentScan("io.talken.dex.shared")
public class DGovRootConfig {

    /**
     * Post launch executor post launch executor.
     *
     * @return the post launch executor
     */
// PostLauncherExecutor
	@Bean
	public PostLaunchExecutor postLaunchExecutor() {
		return new PostLaunchExecutor();
	}

    /**
     * Message source message source.
     *
     * @return the message source
     */
// Message Source & Service
	@Bean
	public MessageSource messageSource() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasenames("i18n/message", "i18n/dexException");
		messageSource.setDefaultEncoding(CommonConsts.CHARSET_NAME);

		return messageSource;
	}

    /**
     * Service status service service status service.
     *
     * @return the service status service
     */
    @Bean
	public ServiceStatusService serviceStatusService() {
		return new ServiceStatusService();
	}

    /**
     * Message service message service.
     *
     * @return the message service
     */
    @Bean
	public MessageService messageService() {
		return new MessageService(CommonConsts.LOCALE, messageSource());
	}
}
