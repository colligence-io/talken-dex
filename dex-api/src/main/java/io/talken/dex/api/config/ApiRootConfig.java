package io.talken.dex.api.config;

import io.talken.common.CommonConsts;
import io.talken.common.service.JWTService;
import io.talken.common.service.MessageService;
import io.talken.common.util.PostLaunchExecutor;
import io.talken.dex.api.ApiSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * The type Api root config.
 */
@Configuration
@ComponentScan("io.talken.dex.shared")
public class ApiRootConfig {

	@Autowired
	private ApiSettings apiSettings;

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
     * Message service message service.
     *
     * @return the message service
     */
    @Bean
	public MessageService messageService() {
		return new MessageService(CommonConsts.LOCALE, messageSource());
	}

    /**
     * Jwt service jwt service.
     *
     * @return the jwt service
     */
    @Bean
	public JWTService jwtService() {
		return new JWTService(apiSettings.getAccessToken().getJwtSecret());
	}
}
