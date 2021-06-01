package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;

import com.mongodb.*;
import com.mongodb.client.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;

public class MongoFacade implements BackendInterface {
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
