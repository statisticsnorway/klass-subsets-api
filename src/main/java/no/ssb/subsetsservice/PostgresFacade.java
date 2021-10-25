package no.ssb.subsetsservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.SQLExec;
import org.postgresql.util.PGobject;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.sql.*;

import static org.springframework.http.HttpStatus.*;

public class PostgresFacade implements BackendInterface {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsControllerV2.class);
    private String JDBC_PS_URL = "jdbc:postgresql://localhost:5432/postgres";
    private String USER = "postgres";
    private String PASSWORD = "postgres";
    private boolean initialized = false;


    private static String getURLFromEnvOrDefault() {
        return System.getenv().getOrDefault("JDBC_PS_URL", "jdbc:postgresql://localhost:5432/postgres");
    }

    private static String getUserFromEnvOrDefault() {
        return System.getenv().getOrDefault("POSTGRES_USER", "postgres");
    }

    private static String getPasswordFromEnvOrDefault() {
        return System.getenv().getOrDefault("PASSWORD", "postgres");
    }

    private static String getCloudSQLURL(String databaseName, String instanceConnectionName, String postgresqlUserName, String postgresqlUserPassword){
        return "jdbc:postgresql:///"+databaseName+"?cloudSqlInstance="+instanceConnectionName+"&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user="+postgresqlUserName+"&password="+postgresqlUserPassword;
    }

    @Override
    public ResponseEntity<JsonNode> initializeBackend() {
        JDBC_PS_URL = getURLFromEnvOrDefault();
        USER = getUserFromEnvOrDefault();
        PASSWORD = getPasswordFromEnvOrDefault();

        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery("SELECT VERSION()");
            if (rs.next()) {
                LOG.debug("'SELECT VERSION()' result : "+rs.getString(1));
            }
        } catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
            return ErrorHandler.newHttpError(ex.getMessage(), INTERNAL_SERVER_ERROR, LOG);
        }

        try {
            LOG.debug("Attempting subsets table creation...");
            executeSql("src/main/sql/subsetsCreate.sql");
        } catch (PSQLException e) {
            LOG.error(e.getMessage(), e);
            e.printStackTrace();
        }

        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            Statement st = con.createStatement();
            String getTablesQuery = "SELECT * FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema='public'";
            LOG.debug("Executing query: '"+getTablesQuery+"'");
            ResultSet rs = st.executeQuery("SELECT * FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema='public'");
            LOG.debug("Printing SQL table(name)s retrieved with query:");
            int columnIndex = 1;
            while (rs.next()) {
                String table = rs.getString(columnIndex);
                LOG.debug("'rs.getString("+columnIndex+"): "+table);
                columnIndex++;
            }
            initialized = true;
            return new ResponseEntity<>(OK);
        } catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
            return ErrorHandler.newHttpError(ex.getMessage(), INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> getVersionByID(String versionId) {
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeries(String id) {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement("SELECT 'seriesJSON' FROM series WHERE 'seriesId'=?;");
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            ObjectMapper om = new ObjectMapper();
            JsonNode series = om.createObjectNode();
            boolean next = rs.next();
            if (!next)
                return ErrorHandler.newHttpError("Series with id"+id+" was not found", NOT_FOUND, LOG);
            if (!rs.isLast())
                LOG.error("There was more than one row in a rs from a query find a series with id "+id);
            series = om.readTree(rs.getString(1));
            return new ResponseEntity<>(series, OK);
        } catch (SQLException ex) {
            LOG.error("Failed to create series", ex);
            return ErrorHandler.newHttpError("Failed to create series", INTERNAL_SERVER_ERROR, LOG);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse json", e);
            return ErrorHandler.newHttpError("Failed to parse json", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> getAllSubsetSeries() {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement("SELECT 'seriesJSON' FROM series;");
            ResultSet rs = pstmt.executeQuery();
            ObjectMapper om = new ObjectMapper();
            ArrayNode series = om.createArrayNode();
            while (rs.next()) {
                series.add(om.readTree(rs.getString(1)));
            }
            return new ResponseEntity<>(series, OK);
        } catch (SQLException ex) {
            LOG.error("Failed to create series", ex);
            return ErrorHandler.newHttpError("Failed to create series", INTERNAL_SERVER_ERROR, LOG);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse json", e);
            return ErrorHandler.newHttpError("Failed to parse json", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public boolean healthReady() {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery("SELECT VERSION()");
            if (rs.next()) {
                LOG.debug("'SELECT VERSION()' result : "+rs.getString(1));
                return true;
            }
            return false;
        } catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public ResponseEntity<JsonNode> editSeries(JsonNode newVersionOfSeries, String seriesID) {
        //TODO
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> createSubsetSeries(JsonNode subset, String id) {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement("insert into series values(?, ?::JSON)");
            pstmt.setString(1, id);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(subset.toString());
            pstmt.setObject(2, jsonObject);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                LOG.debug("insert into series affected "+affectedRows+" rows");
                return new ResponseEntity<>(CREATED);
            }
            return ErrorHandler.newHttpError("No rows were affected", INTERNAL_SERVER_ERROR, LOG);
        } catch (SQLException ex) {
            LOG.error("Failed to create series", ex);
            return ErrorHandler.newHttpError("Failed to create series", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> postVersionInSeries(String id, String versionID, JsonNode versionNode) {
        //TODO
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> resolveVersionLink(String versionLink) {
        return null;
    }

    @Override
    public boolean existsSubsetSeriesWithID(String id) {
        return false;
    } //TODO

    @Override
    public ResponseEntity<JsonNode> getSubsetSeriesDefinition() {
        return null;
    } //TODO

    @Override
    public ResponseEntity<JsonNode> getSubsetSeriesSchema() {
        return null;
    } //TODO

    @Override
    public ResponseEntity<JsonNode> getSubsetVersionsDefinition() {
        return null;
    } //TODO

    @Override
    public ResponseEntity<JsonNode> getSubsetVersionSchema() {
        return null;
    } //TODO

    @Override
    public ResponseEntity<JsonNode> getSubsetCodeDefinition() {
        return null;
    } //TODO

    @Override
    public ResponseEntity<JsonNode> deleteAllSubsetSeries() {
        return null;
    } //TODO

    @Override
    public ResponseEntity<JsonNode> deleteSubsetSeries(String id) {
        return null;
    } //TODO

    @Override
    public void deleteSubsetVersion(String subsetId, String versionUid) {
        //TODO
    }

    @Override
    public ResponseEntity<JsonNode> editVersion(ObjectNode editablePutVersion) {
        return null;
    } //TODO

    private void executeSql(String sqlFilePath) throws PSQLException {
        final class SqlExecutor extends SQLExec {
            public SqlExecutor() {
                Project project = new Project();
                project.init();
                setProject(project);
                setTaskType("sql");
                setTaskName("sql");
            }
        }

        SqlExecutor executor = new SqlExecutor();
        File sqlFile = new File(sqlFilePath);
        LOG.debug("SQL file absolute path: "+sqlFile.getAbsolutePath());
        if (!sqlFile.exists())
            throw new Error("SQL file does not exist at given path! "+sqlFile.getAbsolutePath());
        executor.setSrc(new File(sqlFilePath));
        executor.setDriver("org.postgresql.Driver");
        executor.setPassword(PASSWORD);
        executor.setUserid(USER);
        executor.setUrl(JDBC_PS_URL);
        LOG.debug("Executing SQL in file: "+sqlFilePath+" which resolves to absolute path "+sqlFile.getAbsolutePath());
        executor.execute();
    }
}
