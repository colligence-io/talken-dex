package io.talken.dex.shared.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataMariaDB;
import io.talken.common.util.PrefixedLogger;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.SQLDialect;
import org.jooq.impl.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;

@Configuration
@ComponentScan("io.talken.common.persistence")
@EnableTransactionManagement
@RequiredArgsConstructor
@EnableMBeanExport(registration=RegistrationPolicy.IGNORE_EXISTING)
public class DataConfig {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(DataConfig.class);

    // BEANS
    private final VaultSecretReader secretReader;

    @Value("${spring.datasource.hikari.pool-name}")
    private String poolName;

    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource() {
        VaultSecretDataMariaDB secret = secretReader.readSecret("mariadb", VaultSecretDataMariaDB.class);

        String jdbcUrl = "jdbc:mariadb://" + secret.getAddr() + ":" + secret.getPort() + "/" + secret.getSchema();

        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .username(secret.getUsername())
                .password(secret.getPassword())
                .url(jdbcUrl)
                .build();

        /**
         * DB x
         * connect_timeout,5
         * net_read_timeout,30
         * wait_timeout,600
         */

        /**
         * add performance tuning properties
         *
         * best practice
         * https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration

         * dataSource.cachePrepStmts=true
         * dataSource.prepStmtCacheSize=250
         * dataSource.prepStmtCacheSqlLimit=2048
         * dataSource.useServerPrepStmts=true
         * dataSource.useLocalSessionState=true
         * dataSource.rewriteBatchedStatements=true
         * dataSource.cacheResultSetMetadata=true
         * dataSource.cacheServerConfiguration=true
         * dataSource.elideSetAutoCommits=true
         * dataSource.maintainTimeStats=false
         *
         */
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
    public HikariPoolMXBean poolProxy() throws MalformedObjectNameException {
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectName objPoolName = new ObjectName("com.zaxxer.hikari:type=Pool (" + poolName + ")");
        return JMX.newMBeanProxy(mbeanServer, objPoolName, HikariPoolMXBean.class);
    }

    @Bean
    public void logPoolStats() {
        try {
            HikariPoolMXBean poolProxy = poolProxy();
            logger.info(
                    "[{}] HikariCP: "
                            + "numBusyConnections = {}, "
                            + "numIdleConnections = {}, "
                            + "numConnections = {}, "
                            + "numThreadsAwaitingCheckout = {}",
                    poolName,
                    poolProxy.getActiveConnections(),
                    poolProxy.getIdleConnections(),
                    poolProxy.getTotalConnections(),
                    poolProxy.getThreadsAwaitingConnection());

        } catch(MalformedObjectNameException e) {
            logger.error("[{}] Unable to log pool statistics.", poolName, e);
        }
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
