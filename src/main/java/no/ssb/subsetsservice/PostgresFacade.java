package no.ssb.subsetsservice;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.io.IOException;
import java.sql.*;

import static org.springframework.http.HttpStatus.*;

public class PostgresFacade implements BackendInterface {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsControllerV2.class);
    private String JDBC_PS_URL = "jdbc:postgresql://localhost:5432/postgres";
    private String USER = "postgres";
    private String PASSWORD = "postgres";
    private boolean initialized = false;


    String SELECT_SERIES_BY_ID = "SELECT series.series_json FROM series WHERE series.series_id = ?;";
    String SELECT_ALL_SERIES = "SELECT series.series_json FROM series;";
    String UPDATE_SERIES = "UPDATE series SET series_json = ? WHERE series_id = ?;";

    String SELECT_VERSION_BY_ID = "SELECT versions.version_json FROM versions WHERE versions.version_id = ?;";
    String SELECT_VERSIONS_BY_SERIES = "SELECT versions.version_json FROM versions WHERE versions.series_id = ?;";
    String UPDATE_VERSION = "UPDATE versions SET version_json = ? WHERE series_id = ? AND version_id = ?;";

    String DELETE_SERIES = "DELETE FROM series;";
    String DELETE_SERIES_BY_ID = "DELETE FROM series WHERE series.series_id = ?;";
    String DELETE_VERSIONS_IN_SERIES = "DELETE FROM versions WHERE versions.series_id = ?;";
    String DELETE_VERSIONS = "DELETE FROM versions;";
    String DELETE_VERSIONS_BY_ID = "DELETE FROM versions WHERE versions.series_id = ? AND versions.version_id = ?;";


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
            con.close();
            if (rs.next()) {
                LOG.debug("'SELECT VERSION()' result : "+rs.getString(1));
            }
        } catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
            return ErrorHandler.newHttpError(ex.getMessage(), INTERNAL_SERVER_ERROR, LOG);
        }

        try {
            LOG.debug("Attempting subsets table creation...");
            executeSql("src/main/resources/sql/subsetsCreate.sql");
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
            con.close();
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
    public ResponseEntity<JsonNode> getVersionByID(String versionUid) {
        LOG.debug("getVersionByID uid "+versionUid);
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement(SELECT_VERSION_BY_ID);
            pstmt.setString(1, versionUid);
            LOG.debug("pstmt: "+pstmt);
            ResultSet rs = pstmt.executeQuery();
            con.close();
            ObjectMapper om = new ObjectMapper();
            JsonNode series;
            boolean next = rs.next();
            if (!next)
                return ErrorHandler.newHttpError("Version with id "+versionUid+" was not found", NOT_FOUND, LOG);
            if (!rs.isLast())
                LOG.error("There was more than one row in a rs from a query find a series version with id "+versionUid);
            series = om.readTree(rs.getString(1));
            return new ResponseEntity<>(series, OK);
        } catch (SQLException ex) {
            LOG.error("Failed to create series", ex);
            return ErrorHandler.newHttpError("Failed to get series version", INTERNAL_SERVER_ERROR, LOG);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse json", e);
            return ErrorHandler.newHttpError("Failed to parse json", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeries(String id) {
        LOG.debug("getSubsetSeries id "+id);
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement(SELECT_SERIES_BY_ID);
            pstmt.setString(1, id);
            LOG.debug("pstmt: "+pstmt);
            ResultSet rs = pstmt.executeQuery();
            con.close();
            ObjectMapper om = new ObjectMapper();
            JsonNode series;
            boolean next = rs.next();
            if (!next)
                return ErrorHandler.newHttpError("Series with id "+id+" was not found", NOT_FOUND, LOG);
            if (!rs.isLast())
                LOG.error("There was more than one row in a rs from a query find a series with id "+id);
            series = om.readTree(rs.getString(1));
            return new ResponseEntity<>(series, OK);
        } catch (SQLException ex) {
            LOG.error("Failed to create series", ex);
            return ErrorHandler.newHttpError("Failed to get series", INTERNAL_SERVER_ERROR, LOG);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse json", e);
            return ErrorHandler.newHttpError("Failed to parse json", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> getAllSubsetSeries() {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement(SELECT_ALL_SERIES);
            ResultSet rs = pstmt.executeQuery();
            con.close();
            ObjectMapper om = new ObjectMapper();
            ArrayNode allSeriesArrayNode = om.createArrayNode();
            LOG.debug("rs get fetch size "+rs.getFetchSize());
            while (rs.next()) {
                String rsGETString = rs.getString(1);
                LOG.debug("rs get string: "+rsGETString);
                allSeriesArrayNode.add(om.readTree(rsGETString));
            }
            return new ResponseEntity<>(allSeriesArrayNode, OK);
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
            con.close();
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
        LOG.debug("editSeries with id "+seriesID);
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement(UPDATE_SERIES);
            pstmt.setString(2, seriesID);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(newVersionOfSeries.toString());
            pstmt.setObject(1, jsonObject);
            int affectedRows = pstmt.executeUpdate();
            con.close();
            if (affectedRows > 0) {
                LOG.debug("edit series affected "+affectedRows+" rows");
                return new ResponseEntity<>(CREATED);
            }
            return ErrorHandler.newHttpError("No rows were affected", INTERNAL_SERVER_ERROR, LOG);
        } catch (SQLException ex) {
            LOG.error("Failed to edit series", ex);
            return ErrorHandler.newHttpError("Failed to edit series", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> createSubsetSeries(JsonNode subset, String id) {
        LOG.debug("createSubsetSeries with id "+id);
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement("insert into series values(?, ?::JSON)");
            pstmt.setString(1, id);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(subset.toString());
            pstmt.setObject(2, jsonObject);
            int affectedRows = pstmt.executeUpdate();
            con.close();
            if (affectedRows > 0) {
                LOG.debug("insert into series affected "+affectedRows+" rows");
                return new ResponseEntity<>(CREATED);
            }
            return ErrorHandler.newHttpError("No rows were affected", INTERNAL_SERVER_ERROR, LOG);
        } catch (SQLException ex) {
            ex.printStackTrace();
            LOG.error("Failed to create series", ex);
            return ErrorHandler.newHttpError("Failed to create series", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> saveVersionInSeries(String seriesID, String versionID, JsonNode versionNode) {
        String versionUID = seriesID+"_"+versionID;
        LOG.debug("Attempting to insert version with UID "+versionUID+" to POSTGRES");

        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement("insert into versions values(?, ?, ?::JSON)");
            pstmt.setString(1, versionUID);
            pstmt.setString(2, seriesID);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(versionNode.toString());
            pstmt.setObject(3, jsonObject);
            int affectedRows = pstmt.executeUpdate();
            con.close();
            if (affectedRows > 0) {
                LOG.debug("insert into series affected "+affectedRows+" rows");
                return new ResponseEntity<>(CREATED);
            }
            return ErrorHandler.newHttpError("No rows were affected", INTERNAL_SERVER_ERROR, LOG);
        } catch (SQLException ex) {
            LOG.error("Failed to create series", ex);
            return ErrorHandler.newHttpError("Failed to create version", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> resolveVersionLink(String versionLink) {

        return ErrorHandler.newHttpError("Method Not Implemented", NOT_IMPLEMENTED, LOG);
    }

    @Override
    public boolean existsSubsetSeriesWithID(String id) {
        LOG.debug("existsSubsetSeriesWithID "+id);
        ResponseEntity<JsonNode> getSeriesRE = getSubsetSeries(id);
        return getSeriesRE.getStatusCode() == HttpStatus.OK;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeriesDefinition() {
        LOG.debug("getSubsetSeriesDefinition()");
        ResponseEntity<JsonNode> versionSchemaRE = getSubsetSeriesSchema();
        if (!versionSchemaRE.getStatusCode().is2xxSuccessful()){
            return versionSchemaRE;
        }
        JsonNode definition = versionSchemaRE.getBody().get("definitions").get("ClassificationSubsetSeries");
        return new ResponseEntity<>(definition, OK);
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeriesSchema() {
        File schemaFile = new File("src/main/resources/definitions/series.json");
        if (!schemaFile.exists())
            LOG.error("schemaFile does not exist!");
        ObjectMapper om = new ObjectMapper();
        try {
            JsonNode seriesSchema = om.readTree(schemaFile);
            return new ResponseEntity<>(seriesSchema, OK);
        } catch (IOException e) {
            e.printStackTrace();
            return ErrorHandler.newHttpError("IOException on reading subset series schema file", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetVersionsDefinition() {
        LOG.debug("getSubsetVersionsDefinition");
        ResponseEntity<JsonNode> versionSchemaRE = getSubsetVersionSchema();
        LOG.debug("");
        if (!versionSchemaRE.getStatusCode().is2xxSuccessful()){
            LOG.error("versionSchemaRE was not successful, status code was "+versionSchemaRE.getStatusCode());
            return versionSchemaRE;
        }
        LOG.debug("getting definitions.ClassificationSubsetVersion");
        JsonNode definition = versionSchemaRE.getBody().get("definitions").get("ClassificationSubsetVersion");
        return new ResponseEntity<>(definition, OK);
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetVersionSchema() {
        LOG.debug("getSubsetVersionSchema");
        File schemaFile = new File("src/main/resources/definitions/version.json");
        if (!schemaFile.exists())
            LOG.error("schemaFile does not exist!");
        ObjectMapper om = new ObjectMapper();
        try {
            LOG.debug("reading tree of schema file");
            JsonNode seriesSchema = om.readTree(schemaFile);
            return new ResponseEntity<>(seriesSchema, OK);
        } catch (IOException e) {
            e.printStackTrace();
            return ErrorHandler.newHttpError("IOException on reading subset version schema file", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetCodeDefinition() {
        LOG.debug("getSubsetCodeDefinition");
        ResponseEntity<JsonNode> versionSchemaRE = getSubsetVersionSchema();
        LOG.debug("");
        if (!versionSchemaRE.getStatusCode().is2xxSuccessful()){
            LOG.error("versionSchemaRE was not successful, status code was "+versionSchemaRE.getStatusCode());
            return versionSchemaRE;
        }
        LOG.debug("getting definitions.ClassificationSubsetVersion");
        JsonNode definition = versionSchemaRE.getBody().get("definitions").get("ClassificationSubsetCode");
        return new ResponseEntity<>(definition, OK);
    }

    @Override
    public ResponseEntity<JsonNode> deleteAllSubsetSeries() {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement(DELETE_VERSIONS);
            LOG.debug("pstmt: "+pstmt);
            try {
                ResultSet rs = pstmt.executeQuery();
            } catch (SQLException ex) {
                LOG.warn("SQLEx: " + ex.getMessage());
            }

            pstmt = con.prepareStatement(DELETE_SERIES);
            LOG.debug("pstmt: "+pstmt);
            try {
                ResultSet rs = pstmt.executeQuery();
            } catch (SQLException ex) {
                LOG.warn("SQLEx: " + ex.getMessage());
            }
            con.close();
            return new ResponseEntity<>(OK);
        } catch (SQLException ex) {
            LOG.error("Failed to delete all versions and series", ex);
            return ErrorHandler.newHttpError("Failed to delete all versions and series", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> deleteSubsetSeries(String id) {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement(DELETE_VERSIONS_IN_SERIES);
            pstmt.setString(1, id);
            LOG.debug("pstmt: "+pstmt);
            try {
                ResultSet rs = pstmt.executeQuery();
            } catch (SQLException ex) {
                LOG.warn("SQLEx: " + ex.getMessage());
            }

            pstmt = con.prepareStatement(DELETE_SERIES_BY_ID);
            pstmt.setString(1, id);
            LOG.debug("pstmt: "+pstmt);
            try {
                ResultSet rs = pstmt.executeQuery();
            } catch (SQLException ex) {
                LOG.warn("SQLEx: " + ex.getMessage());
            }
            con.close();
            return new ResponseEntity<>(OK);
        } catch (SQLException ex) {
            LOG.error("Failed to delete all series", ex);
            return ErrorHandler.newHttpError("Failed to delete all series", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public void deleteSubsetVersion(String subsetId, String versionUid) {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement(DELETE_VERSIONS_BY_ID);
            pstmt.setString(1, subsetId);
            pstmt.setString(2, versionUid);
            LOG.debug("pstmt: "+pstmt);
            ResultSet rs = pstmt.executeQuery();
            con.close();
        } catch (SQLException ex) {
            LOG.warn("SQLEx: " + ex.getMessage());
            LOG.error("Failed to delete version of series "+subsetId+" with versionUid"+versionUid, ex);
        }
    }

    @Override
    public ResponseEntity<JsonNode> editVersion(ObjectNode editablePutVersion) {
        String seriesID = editablePutVersion.get(Field.SUBSET_ID).asText();
        String versionNr = editablePutVersion.get(Field.VERSION_ID).asText();
        String versionUid = seriesID+"_"+versionNr;
        LOG.debug("editVersion "+versionUid);
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement(UPDATE_VERSION);
            pstmt.setString(2, seriesID);
            pstmt.setString(3, versionUid);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(editablePutVersion.toString());
            pstmt.setObject(1, jsonObject);
            int affectedRows = pstmt.executeUpdate();
            con.close();
            if (affectedRows > 0) {
                LOG.debug("edit version affected "+affectedRows+" rows");
                return new ResponseEntity<>(CREATED);
            }
            return ErrorHandler.newHttpError("No rows were affected", INTERNAL_SERVER_ERROR, LOG);
        } catch (SQLException ex) {
            LOG.error("Failed to edit version", ex);
            return ErrorHandler.newHttpError("Failed to edit version", INTERNAL_SERVER_ERROR, LOG);
        }
    }

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
