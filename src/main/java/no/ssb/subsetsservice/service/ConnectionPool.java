package no.ssb.subsetsservice.service;

import com.google.cloud.sql.postgres.SocketFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.Properties;

public class ConnectionPool {

    private static ConnectionPool instance;

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPool.class);

    private static final String ENV_DB_NAME = "POSTGRES_DB_NAME"; // The ENV var containing the database name
    private static final String ENV_DB_USERNAME = "POSTGRES_USER"; // The ENV var containing the database user
    private static final String ENV_DB_PASSWORD = "POSTGRES_PASSWORD"; // The ENV var containing the users password
    private static final String ENV_DB_CONNECTION_NAME = "POSTGRES_CONNECTION_NAME"; // The ENV var containing the cloud SQL instance name

    private static final String LOCAL_PS_USER = "subsets";
    private static final String LOCAL_PS_PW = "postgres";
    private static final String LOCAL_DB_NAME = "subsets";
    private static final String LOCAL_JDBC_PS_URL = "jdbc:postgresql://localhost:5432/"+LOCAL_DB_NAME;

    private String db_name;
    private String user;
    private String password;
    private String cloudSqlInstance;

    private HikariDataSource connectionPool;

    public static ConnectionPool getInstance(){
        if (instance == null)
            instance = new ConnectionPool();
        return instance;
    };

    private ConnectionPool(){
        db_name = System.getenv().getOrDefault(ENV_DB_NAME, LOCAL_DB_NAME);
        user = System.getenv().getOrDefault(ENV_DB_USERNAME, LOCAL_PS_USER);
        password = System.getenv().getOrDefault(ENV_DB_PASSWORD, LOCAL_PS_PW);
        cloudSqlInstance = System.getenv(ENV_DB_CONNECTION_NAME);
        if (cloudSqlInstance == null) {
            LOG.warn(ENV_DB_CONNECTION_NAME+" env variable was not found. Connection to a local postgres instance will be attempted.");
        } else {
            connectionPool = createConnectionPool(db_name, user, password, cloudSqlInstance);
        }
    }


    private static HikariDataSource createConnectionPool(String db_name, String user, String password, String cloudSqlInstance){
        LOG.debug("createConnectionPool - db: "+db_name+" user: "+user+" cloudSqlInstance: "+cloudSqlInstance);
        // Set up URL parameters
        String jdbcURL = String.format("jdbc:postgresql:///%s", db_name);
        Properties connProps = new Properties();
        connProps.setProperty("user", user); // "postgres-iam-user@gmail.com"
        connProps.setProperty("password", password);
        connProps.setProperty("sslmode", "disable");
        connProps.setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory");
        connProps.setProperty("cloudSqlInstance", cloudSqlInstance); // "project:region:instance"
        connProps.setProperty("enableIamAuth", "true");

        // Initialize connection pool
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcURL);
        config.setDataSourceProperties(connProps);
        config.setConnectionTimeout(10000); // 10s

        return new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        if (connectionPool != null)
            return connectionPool.getConnection();
        else
            return DriverManager.getConnection(LOCAL_JDBC_PS_URL, LOCAL_PS_USER, LOCAL_PS_PW);
    }
}
