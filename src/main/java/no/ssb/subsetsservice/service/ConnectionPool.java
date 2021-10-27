package no.ssb.subsetsservice.service;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.sql.postgres.SocketFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import no.ssb.subsetsservice.controller.ErrorHandler;
import no.ssb.subsetsservice.controller.SubsetsControllerV2;
import no.ssb.subsetsservice.entity.SQL;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.SQLExec;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Properties;

import static org.postgresql.jdbc.EscapedFunctions.USER;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;

public class ConnectionPool {

    private static ConnectionPool instance;

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPool.class);

    private static final String ENV_DB_NAME = "POSTGRES_DB_NAME"; // The ENV var containing the database name
    private static final String ENV_DB_USERNAME = "POSTGRES_USER"; // The ENV var containing the database user
    private static final String ENV_DB_PASSWORD = "POSTGRES_PASSWORD"; // The ENV var containing the users password
    private static final String ENV_DB_CONNECTION_NAME = "POSTGRES_CONNECTION_NAME"; // The ENV var containing the cloud SQL instance name

    private static final String LOCAL_PS_USER = "postgres_klass";
    private static final String LOCAL_PS_PW = "postgres";
    private static final String LOCAL_DB_NAME = "postgres_klass";
    private static final String LOCAL_JDBC_PS_URL = "jdbc:postgresql://localhost:5432/"+LOCAL_DB_NAME;

    private String db_name;
    private String user;
    private String password;
    private String cloudSqlInstance;

    private boolean sourceIsCloudSql = false;

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
            sourceIsCloudSql = true;
            connectionPool = createConnectionPool(db_name, user, password, cloudSqlInstance);
        }
        initializeBackend();
    }


    private static HikariDataSource createConnectionPool(String db_name, String user, String password, String cloudSqlInstance){
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

    private ResponseEntity<JsonNode> initializeBackend() {
        try {
            Connection con = getConnection();
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery("SELECT VERSION()");
            con.close();
            if (rs.next()) {
                LOG.debug("'SELECT VERSION()' result : "+rs.getString(1));
            }
        } catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
            return ErrorHandler.newHttpError(ex.getMessage(), INTERNAL_SERVER_ERROR, LOG);
        }

        try {
            LOG.debug("Attempting subsets table and index creation...");
            Connection con = getConnection();
            PreparedStatement preparedStatement = con.prepareStatement(SQL.CREATE_SERIES);
            preparedStatement.executeQuery();

            preparedStatement = con.prepareStatement(SQL.SET_OWNER_SERIES);
            preparedStatement.executeQuery();

            preparedStatement = con.prepareStatement(SQL.CREATE_VERSIONS);
            preparedStatement.executeQuery();

            preparedStatement = con.prepareStatement(SQL.SET_OWNER_VERSIONS);
            preparedStatement.executeQuery();

            preparedStatement = con.prepareStatement(SQL.CREATE_INDEX);
            preparedStatement.executeQuery();
            con.close();
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
            e.printStackTrace();
        }

        try {
            Connection con = getConnection();
            Statement st = con.createStatement();
            String getTablesQuery = "SELECT * FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema='public'";
            LOG.debug("Executing query: '"+getTablesQuery+"'");
            ResultSet rs = st.executeQuery("SELECT * FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema='public'");
            con.close();
            LOG.debug("Printing SQL table(name)s retrieved with query:");
            int columnIndex = 1;
            while (rs.next()) {
                String table = rs.getString(columnIndex);
                LOG.debug("'rs.getString("+columnIndex+"): "+table);
                columnIndex++;
            }
            return new ResponseEntity<>(OK);
        } catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
            return ErrorHandler.newHttpError(ex.getMessage(), INTERNAL_SERVER_ERROR, LOG);
        }
    }

    public Connection getConnection() throws SQLException {
        if (sourceIsCloudSql)
            return connectionPool.getConnection();
        else
            return DriverManager.getConnection(LOCAL_JDBC_PS_URL, LOCAL_PS_USER, LOCAL_PS_PW);
    }


    private void executeSql(String sqlFilePath) throws PSQLException {
        Path path = Paths.get(sqlFilePath);
        try {
            String sqlString = Files.readString(path);
            try {
                Connection con = getConnection();
                PreparedStatement preparedStatement = con.prepareStatement(sqlString);
                LOG.debug("Executing SQL in file: "+sqlFilePath+" which resolves to absolute path "+path.toAbsolutePath());
                preparedStatement.executeQuery();
                con.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            LOG.error("Failed to read sql file from path string "+sqlFilePath+" with absolute path "+path.toAbsolutePath());
            e.printStackTrace();
        }
    }
}
