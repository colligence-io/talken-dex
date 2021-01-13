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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
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
public class DataConfig {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(DataConfig.class);

    // BEANS
    private final Environment env;
    private final VaultSecretReader secretReader;

    private final String poolName = env.getProperty("spring.application.name") + "_pool";

    @Bean(name = "dataSource")
    @Primary
    public DataSource dataSource() {
        VaultSecretDataMariaDB secret = secretReader.readSecret("mariadb", VaultSecretDataMariaDB.class);

        String jdbcUrl = "jdbc:mariadb://" + secret.getAddr() + ":" + secret.getPort() + "/" + secret.getSchema();

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(secret.getUsername());
        ds.setPassword(secret.getPassword());
        ds.setPoolName(poolName);

        ds.setMaxLifetime(580000);
        ds.setMaximumPoolSize(16);
        ds.setRegisterMbeans(true);
        ds.setConnectionTestQuery("SELECT 1");
        ds.setDriverClassName("org.mariadb.jdbc.Driver");

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
         *
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
         */
        ds.addDataSourceProperty("dataSource.cachePrepStmts", "true");
        ds.addDataSourceProperty("dataSource.prepStmtCacheSize", "250");
        ds.addDataSourceProperty("dataSource.prepStmtCacheSqlLimit", "2048");
        ds.addDataSourceProperty("dataSource.useServerPrepStmts", "true");
//		ds.addDataSourceProperty("dataSource.useLocalSessionState", "true");
//		ds.addDataSourceProperty("dataSource.rewriteBatchedStatements", "true");
//		ds.addDataSourceProperty("dataSource.cacheResultSetMetadata", "true");
//		ds.addDataSourceProperty("dataSource.cacheServerConfiguration", "true");
//		ds.addDataSourceProperty("dataSource.elideSetAutoCommits", "true");
//		ds.addDataSourceProperty("dataSource.maintainTimeStats", "false");

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
    public HikariPoolMXBean initPoolMbeans() throws MalformedObjectNameException {
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectName objPoolName = new ObjectName("com.zaxxer.hikari:type=Pool (" + poolName + ")");
        return JMX.newMBeanProxy(mbeanServer, objPoolName, HikariPoolMXBean.class);
    }

    @Bean
    public void logPoolStats() {
        try {
            HikariPoolMXBean poolProxy = initPoolMbeans();
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