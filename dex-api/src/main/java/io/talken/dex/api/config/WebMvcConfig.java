package io.talken.dex.api.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.talken.common.CommonConsts;
import io.talken.common.util.LocalDateTime2TimestampSerializer;
import io.talken.dex.api.config.auth.AccessTokenInterceptor;
import io.talken.dex.api.config.auth.AuthInfo;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The type Web mvc config.
 */
@Configuration
@EnableWebMvc
public class WebMvcConfig implements WebMvcConfigurer, WebMvcRegistrations {
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tokenInterceptor());
	}

    /**
     * Token interceptor access token interceptor.
     *
     * @return the access token interceptor
     */
    @Bean
	public AccessTokenInterceptor tokenInterceptor() {
		return new AccessTokenInterceptor();
	}

    /**
     * Mapping jackson 2 http message converter mapping jackson 2 http message converter.
     *
     * @return the mapping jackson 2 http message converter
     */
    @Bean
	public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
		ObjectMapper mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(LocalDateTime.class, new LocalDateTime2TimestampSerializer());
		mapper.registerModule(module);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return new MappingJackson2HttpMessageConverter(mapper);
	}

	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.add(mappingJackson2HttpMessageConverter());
	}

    /**
     * Character encoding filter filter.
     *
     * @return the filter
     */
    @Bean
	public Filter characterEncodingFilter() {
		CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
		characterEncodingFilter.setEncoding(CommonConsts.CHARSET_NAME);
		characterEncodingFilter.setForceEncoding(true);
		return characterEncodingFilter;
	}

    /**
     * Request scoped auth info bean auth info.
     *
     * @return the auth info
     */
    @Bean
	@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
	public AuthInfo requestScopedAuthInfoBean() {
		return new AuthInfo();
	}
}
