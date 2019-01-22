package io.colligence.talken.dex.config;

import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebServerConfig implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {
	@Override
	public void customize(ConfigurableServletWebServerFactory factory) {
//		factory.addErrorPages(new ErrorPage(HttpStatus.BAD_REQUEST, "/error/400"));
//		factory.addErrorPages(new ErrorPage(HttpStatus.UNAUTHORIZED, "/error/401"));
//		factory.addErrorPages(new ErrorPage(HttpStatus.FORBIDDEN, "/error/403"));
//		factory.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/error/404"));
//		factory.addErrorPages(new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/error/500"));
//		factory.addErrorPages(new ErrorPage(Exception.class, "/error/exception"));
//		factory.addErrorPages(new ErrorPage("/error"));
	}

	@Bean
	public JettyServletWebServerFactory jettyServletWebServerFactory() {
		JettyServletWebServerFactory jetty = new JettyServletWebServerFactory();
//        jetty.setPort(9000);
//        jetty.setContextPath("/springbootapp");
		return jetty;
	}
}
