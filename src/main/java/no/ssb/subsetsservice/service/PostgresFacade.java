package no.ssb.subsetsservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.ssb.subsetsservice.controller.ErrorHandler;
import no.ssb.subsetsservice.entity.Field;
import no.ssb.subsetsservice.entity.SQL;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.SQLExec;
import org.postgresql.util.PGobject;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.sql.*;

import static no.ssb.subsetsservice.entity.SQL.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class PostgresFacade implements DatabaseInterface {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresFacade.class);

    ConnectionPool connectionPool;

    private static final String LOCAL_SUBSETS_SCHEMA_DIR = "src/main/resources/definitions/";
    private static final String DOCKER_SUBSETS_SCHEMA_DIR = "/usr/share/klass-subsets-api/";

    private static final String VERSION_SCHEMA_FILENAME = "version.json";
    private static final String SERIES_SCHEMA_FILENAME = "series.json";

    private String SUBSETS_SCHEMA_DIR = LOCAL_SUBSETS_SCHEMA_DIR;
    private String VERSION_SCHEMA_PATH = LOCAL_SUBSETS_SCHEMA_DIR+VERSION_SCHEMA_FILENAME;
    private String SERIES_SCHEMA_PATH = LOCAL_SUBSETS_SCHEMA_DIR+SERIES_SCHEMA_FILENAME;

    public PostgresFacade(){
        ResponseEntity<JsonNode> initBackendRE = initializeDatabase();
    }

    @Override
    public ResponseEntity<JsonNode> initializeDatabase() {
        LOG.debug("Finding schema");
        String versionSchemaJsonPath = LOCAL_SUBSETS_SCHEMA_DIR+VERSION_SCHEMA_FILENAME;
        //String seriesSchemaJsonPath = LOCAL_SUBSETS_SCHEMA_DIR+SERIES_SCHEMA_FILENAME;
        File versionJsonFile = new File(versionSchemaJsonPath);
        //File seriesJsonFile = new File(seriesSchemaJsonPath);
        if (versionJsonFile.exists() && versionJsonFile.isFile()) {
            LOG.debug("version schema file "+versionJsonFile.getPath()+" exists and is a file!");
            LOG.debug("Setting schema directory to "+LOCAL_SUBSETS_SCHEMA_DIR);
            SUBSETS_SCHEMA_DIR = LOCAL_SUBSETS_SCHEMA_DIR;
        }
        else {
            versionSchemaJsonPath = DOCKER_SUBSETS_SCHEMA_DIR+VERSION_SCHEMA_FILENAME;
            versionJsonFile = new File(versionSchemaJsonPath);
            if (versionJsonFile.exists() && versionJsonFile.isFile()) {
                LOG.debug(versionJsonFile.getPath()+" exists and is a file!");
                LOG.debug("Setting schema directory to "+DOCKER_SUBSETS_SCHEMA_DIR);
                SUBSETS_SCHEMA_DIR = DOCKER_SUBSETS_SCHEMA_DIR;
            } else {
                String schemaErrorString = "Could not locate versions.json schema file in "+DOCKER_SUBSETS_SCHEMA_DIR+" which is the docker default, or "+LOCAL_SUBSETS_SCHEMA_DIR+" which is the local testing location.";
                LOG.error(schemaErrorString);
                throw new Error(schemaErrorString);
            }
        }
        VERSION_SCHEMA_PATH = SUBSETS_SCHEMA_DIR+VERSION_SCHEMA_FILENAME;
        SERIES_SCHEMA_PATH = SUBSETS_SCHEMA_DIR+SERIES_SCHEMA_FILENAME;

        LOG.debug("initializeDatabase in PostgresFacade");
        connectionPool = ConnectionPool.getInstance();
        try (Connection con = connectionPool.getConnection()) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery("SELECT VERSION()");
            if (rs.next()) {
                LOG.debug("'SELECT VERSION()' result : "+rs.getString(1));
            }

            LOG.debug("Attempting subsets table and index creation...");
            PreparedStatement preparedStatement = con.prepareStatement(SQL.CREATE_SERIES);
            LOG.debug("Crete series table");
            preparedStatement.executeUpdate();

            preparedStatement = con.prepareStatement(SQL.SET_OWNER_SERIES);
            LOG.debug("Set owner of series table");
            preparedStatement.executeUpdate();

            preparedStatement = con.prepareStatement(SQL.CREATE_VERSIONS);
            LOG.debug("create versions table");
            preparedStatement.executeUpdate();

            preparedStatement = con.prepareStatement(SQL.SET_OWNER_VERSIONS);
            LOG.debug("set owner of versions table");
            preparedStatement.executeUpdate();

            preparedStatement = con.prepareStatement(SQL.CREATE_INDEX);
            LOG.debug("create index");
            preparedStatement.executeUpdate();

            st = con.createStatement();
            String getTablesQuery = "SELECT * FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema='public'";
            LOG.debug("Executing query: '"+getTablesQuery+"'");
            ResultSet rs = st.executeQuery(getTablesQuery);
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

    @Override
    public ResponseEntity<JsonNode> getVersionByID(String versionUid) {
        LOG.debug("getVersionByID uid "+versionUid);
        try (Connection con = connectionPool.getConnection()) {
            PreparedStatement pstmt = con.prepareStatement(SELECT_VERSION_BY_ID);
            pstmt.setString(1, versionUid);
            LOG.debug("pstmt: "+pstmt);
            ResultSet rs = pstmt.executeQuery();
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
        try (Connection con = connectionPool.getConnection()) {
            PreparedStatement pstmt = con.prepareStatement(SELECT_SERIES_BY_ID);
            pstmt.setString(1, id);
            LOG.debug("pstmt: "+pstmt);
            ResultSet rs = pstmt.executeQuery();
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
        try (Connection con = connectionPool.getConnection()) {
            PreparedStatement pstmt = con.prepareStatement(SELECT_ALL_SERIES);
            ResultSet rs = pstmt.executeQuery();
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
        try (Connection con = connectionPool.getConnection()) {
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
        LOG.debug("editSeries with id "+seriesID);
        try (Connection con = connectionPool.getConnection()) {
            PreparedStatement pstmt = con.prepareStatement(UPDATE_SERIES);
            pstmt.setString(2, seriesID);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(newVersionOfSeries.toString());
            pstmt.setObject(1, jsonObject);
            int affectedRows = pstmt.executeUpdate();
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
        try (Connection con = connectionPool.getConnection()) {
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
            ex.printStackTrace();
            LOG.error("Failed to create series", ex);
            return ErrorHandler.newHttpError("Failed to create series", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> saveVersionInSeries(String seriesID, String versionID, JsonNode versionNode) {
        String versionUID = seriesID+"_"+versionID;
        LOG.debug("Attempting to insert version with UID "+versionUID+" to POSTGRES and update series to point to version");

        try (Connection con = connectionPool.getConnection()) {
            PreparedStatement pstmt = con.prepareStatement("insert into versions values(?, ?, ?::JSON)");
            pstmt.setString(1, versionUID);
            pstmt.setString(2, seriesID);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(versionNode.toString());
            pstmt.setObject(3, jsonObject);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                LOG.debug("insert into versions affected "+affectedRows+" rows");
            } else {
                return ErrorHandler.newHttpError("No rows were affected by insert into versions", INTERNAL_SERVER_ERROR, LOG);
            }
            LOG.debug("preparing statement to add version to series");
            pstmt = con.prepareStatement(ADD_VERSION_TO_SERIES);
            pstmt.setString(1, versionUID);
            pstmt.setString(2, seriesID);
            LOG.debug("pstmt: "+pstmt);
            int affectedRowsUpdateSeries = pstmt.executeUpdate();
            if (affectedRowsUpdateSeries > 0) {
                LOG.debug("update series with version affected "+affectedRows+" rows");
            } else {
                return ErrorHandler.newHttpError("No rows were affected by update into series", INTERNAL_SERVER_ERROR, LOG);
            }
            return new ResponseEntity<>(CREATED);
        } catch (SQLException ex) {
            ex.printStackTrace();
            LOG.error("Failed to create version or insert version into series", ex);
            return ErrorHandler.newHttpError("Failed to create version or insert version into series", INTERNAL_SERVER_ERROR, LOG);
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
        File schemaFile = new File(SERIES_SCHEMA_PATH);
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
        File schemaFile = new File(VERSION_SCHEMA_PATH);
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
        try (Connection con = connectionPool.getConnection()) {
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
                pstmt.executeQuery();
            } catch (SQLException ex) {
                LOG.warn("SQLEx: " + ex.getMessage());
            }
            return new ResponseEntity<>(OK);
        } catch (SQLException ex) {
            LOG.error("Failed to delete all versions and series", ex);
            return ErrorHandler.newHttpError("Failed to delete all versions and series", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public ResponseEntity<JsonNode> deleteSubsetSeries(String id) {
        try (Connection con = connectionPool.getConnection()) {
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
            return new ResponseEntity<>(OK);
        } catch (SQLException ex) {
            LOG.error("Failed to delete all series", ex);
            return ErrorHandler.newHttpError("Failed to delete all series", INTERNAL_SERVER_ERROR, LOG);
        }
    }

    @Override
    public void deleteSubsetVersion(String subsetId, String versionUid) {
        try (Connection con = connectionPool.getConnection()) {
            PreparedStatement pstmt = con.prepareStatement(DELETE_VERSIONS_BY_ID);
            pstmt.setString(1, subsetId);
            pstmt.setString(2, versionUid);
            LOG.debug("pstmt: "+pstmt);
            pstmt.executeQuery();
        } catch (SQLException ex) {
            LOG.error("Failed to delete version of series "+subsetId+" with versionUid"+versionUid, ex);
        }
    }

    @Override
    public ResponseEntity<JsonNode> editVersion(ObjectNode editablePutVersion) {
        String seriesID = editablePutVersion.get(Field.SUBSET_ID).asText();
        String versionNr = editablePutVersion.get(Field.VERSION_ID).asText();
        String versionUid = seriesID+"_"+versionNr;
        LOG.debug("editVersion "+versionUid);
        try (Connection con = connectionPool.getConnection()) {
            PreparedStatement pstmt = con.prepareStatement(UPDATE_VERSION);
            pstmt.setString(2, seriesID);
            pstmt.setString(3, versionUid);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(editablePutVersion.toString());
            pstmt.setObject(1, jsonObject);
            int affectedRows = pstmt.executeUpdate();
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
}
