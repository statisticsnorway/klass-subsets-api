package no.ssb.subsetsservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.*;

import static org.springframework.http.HttpStatus.*;

public class PostgresFacade implements BackendInterface {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsControllerV2.class);
    private String JDBC_PS_URL = "jdbc:postgresql://localhost:5432/postgres";
    private String USER = "postgres";
    private String PASSWORD = "postgres";


    private static String getURLFromEnvOrDefault() {
        return System.getenv().getOrDefault("JDBC_PS_URL", "jdbc:postgresql://localhost:5432/postgres");
    }

    private static String getUserFromEnvOrDefault() {
        return System.getenv().getOrDefault("POSTGRES_USER", "postgres");
    }

    private static String getPasswordFromEnvOrDefault() {
        return System.getenv().getOrDefault("PASSWORD", "postgres");
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
                System.out.println("'SELECT VERSION()' result : "+rs.getString(1));
            }

        } catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        //TODO: Send the subsetsCreate.sql query to the PostgreSQL server

        return new ResponseEntity<>(OK);
    }

    @Override
    public ResponseEntity<JsonNode> getVersionByID(String versionId) {
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeries(String id) {
        try {
            Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
            PreparedStatement pstmt = con.prepareStatement("SELECT 'seriesJSON' FROM series");
            ResultSet rs = pstmt.executeQuery();
            ObjectMapper om = new ObjectMapper();
            ArrayNode series = om.createArrayNode();
            while (rs.next()) {
                series.add(om.readTree(rs.getString(1)));
            }
            return new ResponseEntity<JsonNode>(series, OK);
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
        return null;
    }

    @Override
    public boolean healthReady() {
        return false;
    }

    @Override
    public ResponseEntity<JsonNode> editSeries(JsonNode newVersionOfSeries, String seriesID) {
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
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> resolveVersionLink(String versionLink) {
        return null;
    }

    @Override
    public boolean existsSubsetSeriesWithID(String id) {
        return false;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeriesDefinition() {
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeriesSchema() {
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetVersionsDefinition() {
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetVersionSchema() {
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetCodeDefinition() {
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> deleteAllSubsetSeries() {
        return null;
    }

    @Override
    public ResponseEntity<JsonNode> deleteSubsetSeries(String id) {
        return null;
    }

    @Override
    public void deleteSubsetVersionFromSeriesAndFromLDS(String id, String versionUid) {

    }

    @Override
    public ResponseEntity<JsonNode> editVersion(ObjectNode editablePutVersion) {
        return null;
    }
}
