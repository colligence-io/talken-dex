package io.colligence.talken.dex.config;

import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.SQLDialect;
import org.jooq.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@ComponentScan("io.colligence.talken.common.persistence")
@EnableTransactionManagement
public class DataConfig {
	@Autowired
	private DataSource dataSource;

	@Bean
	public DataSourceConnectionProvider dataSourceConnectionProvider() {
		return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource));
	}

	@Bean
	public DSLContext dsl() {
		return new DefaultDSLContext(configuration());
	}

	@Bean
	public DefaultConfiguration configuration() {
		DefaultConfiguration config = new DefaultConfiguration();
		config.set(SQLDialect.MARIADB);
		config.set(dataSourceConnectionProvider());
		config.set(new DefaultExecuteListenerProvider(exceptionTransformer()));

		return config;
	}

	@Bean
	public ExceptionTranslator exceptionTransformer() {
		return new ExceptionTranslator();
	}

	public class ExceptionTranslator extends DefaultExecuteListener {
		public void exception(ExecuteContext context) {
			SQLDialect dialect = context.configuration().dialect();
			SQLExceptionTranslator translator = new SQLErrorCodeSQLExceptionTranslator(dialect.name());
			context.exception(translator.translate("jOOQ", context.sql(), context.sqlException()));
		}
	}
}
