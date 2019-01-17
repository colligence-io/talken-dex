package io.colligence.talken.dex.config;

import io.colligence.talken.common.CommonConsts;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;

@Configuration
@EnableWebMvc
public class WebMvcConfig implements WebMvcConfigurer, WebMvcRegistrations {
	@Override
	public void addInterceptors(InterceptorRegistry registry) {

//		registry.addInterceptor(localeChangeInterceptor());
	}

	@Bean
	public HttpMessageConverter<String> responseBodyConverter() {
		return new StringHttpMessageConverter(CommonConsts.CHARSET);
	}

	@Bean
	public Filter characterEncodingFilter() {
		CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
		characterEncodingFilter.setEncoding(CommonConsts.CHARSET_NAME);
		characterEncodingFilter.setForceEncoding(true);
		return characterEncodingFilter;
	}
}
