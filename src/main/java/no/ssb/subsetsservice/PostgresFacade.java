package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.sql.*;

import static org.springframework.http.HttpStatus.OK;

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
        USER = "postgres";
        PASSWORD = "postgres";

        try (Connection con = DriverManager.getConnection(JDBC_PS_URL, USER, PASSWORD);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT VERSION()")) {

            if (rs.next()) {
                System.out.println(rs.getString(1));
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
        return null;
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
        return null;
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
