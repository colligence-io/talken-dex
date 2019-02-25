package io.colligence.talken.dex.config;

import com.zaxxer.hikari.HikariDataSource;
import io.colligence.talken.common.persistence.vault.VaultSecretDataMariaDB;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.SQLDialect;
import org.jooq.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
	private VaultConfig.VaultSecretReader secretReader;

	@Bean(name = "dataSource")
	@Primary
	public DataSource dataSource() {
		VaultSecretDataMariaDB secret = secretReader.readSecret("mariadb", VaultSecretDataMariaDB.class);

		String jdbcUrl = "jdbc:mariadb://" + secret.getAddr() + ":" + secret.getPort() + "/" + secret.getSchema();

		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(jdbcUrl);
		ds.setUsername(secret.getUsername());
		ds.setPassword(secret.getPassword());
		ds.setMinimumIdle(4);
		ds.setMaximumPoolSize(8);
		ds.setMaxLifetime(590000);
		ds.setIdleTimeout(60000);
		ds.setDriverClassName("org.mariadb.jdbc.Driver");
		return ds;
	}

	@Bean
	public DataSourceConnectionProvider dataSourceConnectionProvider() {
		return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource()));
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
