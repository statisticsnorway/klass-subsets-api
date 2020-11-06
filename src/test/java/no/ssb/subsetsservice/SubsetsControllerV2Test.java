package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubsetsControllerV2Test {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsServiceApplicationTests.class);
    File series_1_0 = new File("src/test/resources/series_examples/series_1_0.json");
    File series_1_1 = new File("src/test/resources/series_examples/series_1_1.json"); // A legal edit of series_1_0

    File version_1_0_1 = new File("src/test/resources/version_examples/version_1_0_1.json");
    File version_1_0_1_nocodes_draft = new File("src/test/resources/version_examples/version_1_0_1_nocodes_draft.json");
    File version_1_0_1_nocodes_open = new File("src/test/resources/version_examples/version_1_0_1_nocodes_open.json");
    File version_1_0_1_1 = new File("src/test/resources/version_examples/version_1_0_1_1.json"); // A legal edit of version_1_0_1

    public JsonNode readJsonFile(File file){
        assert file.exists() : "File "+file.getAbsolutePath()+" did not exist";
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readTree(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @AfterAll
    public static void cleanUp(){
        System.out.println("After All cleanUp() method called");
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
    }

    @Test
    void postSubsetSeries() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());
    }

    @Test
    void getAllSubsetSeries() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        ResponseEntity<JsonNode> getSeriesRE = instance.getSubsetSeries( true, true, true);
        assertEquals(HttpStatus.OK, getSeriesRE.getStatusCode());
        JsonNode body = getSeriesRE.getBody();
        assertNotNull(body);
        assertTrue(body.isArray());
        ArrayNode bodyArrayNode = body.deepCopy();
        assertEquals(1, bodyArrayNode.size());
    }

    @Test
    void getSubsetSeriesByID() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        ResponseEntity<JsonNode> getSeriesRE = instance.getSubsetSeriesByID(seriesId, false);
        assertEquals(HttpStatus.OK, getSeriesRE.getStatusCode());
        JsonNode body = getSeriesRE.getBody();
        assertNotNull(body);

        //TODO: Make sure all the right fields have been added to the series
    }

    @Test
    void putSubsetSeries() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        JsonNode series1_1 = readJsonFile(series_1_1);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        ResponseEntity<JsonNode> putSeriesRE = instance.putSubsetSeries(seriesId, series1_1);
        assertEquals(HttpStatus.OK, putSeriesRE.getStatusCode());

        //TODO: Make sure versions list was not overwritten, and createdDate automatically carried over.

        //TODO: Check with a GET that the changes actually happened
    }

    @Test
    void postSubsetVersion() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        //TODO: Make sure all the right fields have been added to the version
    }


    @Test
    void getSubsetVersions() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();

        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        try {
            Thread.sleep(10); //To make sure the resource is available from LDS before we GET it
        } catch (InterruptedException e) {
            fail("Sleep failed");
        }
        ResponseEntity<JsonNode> getVersionsRE = instance.getVersions(seriesId, true, true);
        assertEquals(HttpStatus.OK, getVersionsRE.getStatusCode());
        JsonNode body = getVersionsRE.getBody();
        assertNotNull(body);
        assertTrue(body.isArray());
        ArrayNode bodyArrayNode = body.deepCopy();
        System.out.println("GET request for all versions of "+seriesId+" returns this Array Node as its body:");
        System.out.println(bodyArrayNode.toPrettyString());
        assertEquals(1, bodyArrayNode.size());
    }

    @Test
    void getSubsetVersionByID() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        ResponseEntity<JsonNode> getVersionNrRE = instance.getVersion(seriesId, "1");
        assertEquals(HttpStatus.OK, getVersionNrRE.getStatusCode());
        JsonNode body1 = getVersionNrRE.getBody();
        assertNotNull(body1);

        ResponseEntity<JsonNode> getVersionFullUuidRE = instance.getVersion(seriesId, seriesId+"_1");
        assertEquals(HttpStatus.OK, getVersionFullUuidRE.getStatusCode());
        JsonNode body2 = getVersionFullUuidRE.getBody();
        assertNotNull(body2);
    }

    @Test
    void putSubsetVersion() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        JsonNode version1_0_1_1 = readJsonFile(version_1_0_1_1);
        ResponseEntity<JsonNode> putVersionRE = instance.putSubsetVersion(seriesId, "1", version1_0_1_1);
        assertEquals(HttpStatus.OK, putVersionRE.getStatusCode());

        ResponseEntity<JsonNode> getVersionRE = instance.getVersion(seriesId, "1");
        JsonNode body = getVersionRE.getBody();
        assertNotNull(body);
        assertTrue(body.has(Field.CODES));
        assertTrue(body.get(Field.CODES).isArray());
        ArrayNode codesArray = (ArrayNode) body.get(Field.CODES);
        assertEquals(2, codesArray.size());
    }

    @Test
    void postDraftNoCodesThenPutOpenNoCodes() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode versionNoCodesDraft = readJsonFile(version_1_0_1_nocodes_draft);
        ResponseEntity<JsonNode> postResponseEntity = instance.postSubsetVersion(seriesId, versionNoCodesDraft);
        assertEquals(HttpStatus.CREATED, postResponseEntity.getStatusCode());

        JsonNode versionNoCodesOpen = readJsonFile(version_1_0_1_nocodes_open);
        ResponseEntity<JsonNode> putResponseEntity = instance.putSubsetVersion(seriesId, "1", versionNoCodesOpen);
        assertEquals(HttpStatus.BAD_REQUEST, putResponseEntity.getStatusCode()); // 0 codes is not allowed in published subset
    }

    @Test
    void postOpenNoCodes(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode versionNoCodesDraft = readJsonFile(version_1_0_1_nocodes_open);
        ResponseEntity<JsonNode> postResponseEntity = instance.postSubsetVersion(seriesId, versionNoCodesDraft);
        assertEquals(HttpStatus.BAD_REQUEST, postResponseEntity.getStatusCode());
        System.out.println(postResponseEntity.getBody().toPrettyString());
    }

    @Test
    void deleteAllSeriesThenCheckThatEmpty() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        ResponseEntity<JsonNode> getVersionByIdRE = instance.getVersion(seriesId, "1");
        assertEquals(HttpStatus.OK, getVersionByIdRE.getStatusCode());

        ResponseEntity<JsonNode> getSubset = instance.getSubsetSeriesByID(seriesId, false);
        assertEquals(HttpStatus.OK, getSubset.getStatusCode());
        JsonNode body = getSubset.getBody();
        assertNotNull(body);
        assertFalse(body.isEmpty());

        ResponseEntity<JsonNode> getAllSubsetSeries = instance.getSubsetSeries(true, true, true);
        assertEquals(HttpStatus.OK, getAllSubsetSeries.getStatusCode());
        JsonNode body1 = getAllSubsetSeries.getBody();
        assertNotNull(body1);
        assertTrue(body1.isArray());
        assertFalse(body1.isEmpty());

        instance.deleteAllSeries();

        ResponseEntity<JsonNode> getSubsetWhenItShouldNotExistRE = instance.getSubsetSeriesByID(seriesId, false);
        assertEquals(HttpStatus.NOT_FOUND, getSubsetWhenItShouldNotExistRE.getStatusCode());

        ResponseEntity<JsonNode> getAllSubsetSeriesShouldBeEmptyRE = instance.getSubsetSeries(true, true, true);
        assertEquals(HttpStatus.OK, getAllSubsetSeriesShouldBeEmptyRE.getStatusCode());
        JsonNode shouldBeEmptyArray = getAllSubsetSeriesShouldBeEmptyRE.getBody();
        assertNotNull(shouldBeEmptyArray);
        assertTrue(shouldBeEmptyArray.isArray());
        assertTrue(shouldBeEmptyArray.isEmpty());

        ResponseEntity<JsonNode> getVersionByIdShouldNotExistRE = instance.getVersion(seriesId, "1");
        assertEquals(HttpStatus.NOT_FOUND, getVersionByIdShouldNotExistRE.getStatusCode());

    }

    @Test
    void deleteSeriesByIdThenCheckThatEmpty() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        ResponseEntity<JsonNode> getVersionByIdRE = instance.getVersion(seriesId, "1");
        assertEquals(HttpStatus.OK, getVersionByIdRE.getStatusCode());

        ResponseEntity<JsonNode> getSubset = instance.getSubsetSeriesByID(seriesId, false);
        assertEquals(HttpStatus.OK, getSubset.getStatusCode());
        JsonNode body = getSubset.getBody();
        assertNotNull(body);
        assertFalse(body.isEmpty());

        ResponseEntity<JsonNode> getAllSubsetSeries = instance.getSubsetSeries(true, true, true);
        assertEquals(HttpStatus.OK, getAllSubsetSeries.getStatusCode());
        JsonNode body1 = getAllSubsetSeries.getBody();
        assertNotNull(body1);
        assertTrue(body1.isArray());
        assertFalse(body1.isEmpty());

        instance.deleteSeriesById(seriesId);

        ResponseEntity<JsonNode> getSubsetWhenItShouldNotExistRE = instance.getSubsetSeriesByID(seriesId, false);
        assertEquals(HttpStatus.NOT_FOUND, getSubsetWhenItShouldNotExistRE.getStatusCode());

        ResponseEntity<JsonNode> getAllSubsetSeriesShouldBeEmptyRE = instance.getSubsetSeries(true, true, true);
        assertEquals(HttpStatus.OK, getAllSubsetSeriesShouldBeEmptyRE.getStatusCode());
        JsonNode shouldBeEmptyArray = getAllSubsetSeriesShouldBeEmptyRE.getBody();
        assertNotNull(shouldBeEmptyArray);
        assertTrue(shouldBeEmptyArray.isArray());
        assertTrue(shouldBeEmptyArray.isEmpty());

        ResponseEntity<JsonNode> getVersionByIdShouldNotExistRE = instance.getVersion(seriesId, "1");
        assertEquals(HttpStatus.NOT_FOUND, getVersionByIdShouldNotExistRE.getStatusCode());
    }


    @Test
    void postDeletePostGetVersionsCheckLength(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        instance.deleteVersionById(seriesId, "1");

        JsonNode version2 = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersion2RE = instance.postSubsetVersion(seriesId, version2);
        assertTrue(postVersion2RE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersion2RE.getStatusCode());

        ResponseEntity<JsonNode> getVersionByIdShouldNotExistRE = instance.getVersion(seriesId, "1");
        assertEquals(HttpStatus.NOT_FOUND, getVersionByIdShouldNotExistRE.getStatusCode());

        ResponseEntity<JsonNode> getVersion2ByIdShouldExistRE = instance.getVersion(seriesId, "2");
        assertEquals(HttpStatus.OK, getVersion2ByIdShouldExistRE.getStatusCode());
    }

    /*
    @Test
    void getSubsetCodesInDateRange() {

    }

    @Test
    void getSubsetCodesAtDate() {
    }

    @Test
    void deleteAllSubsetSeriesAndTheirVersions() {
    }

    @Test
    void deleteSubsetSeriesByIdAndItsVersions() {
    }

    @Test
    void deleteSubsetVersionById() {
    }

    @Test
    void validateVersion() {
    }

    @Test
    void validateSeries() {
    }


    @Test
    void postDraftNoCodes(){
    }


    @Test
    void postSubsetAndCheckIDOfResponse(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fCS1);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());
        assertEquals(subsetJsonNode.get(Field.ID).asText(), postRE.getBody().get(Field.ID).asText());
        System.out.println(postRE.getBody().toPrettyString());
        String originalID = subsetJsonNode.get(Field.ID).asText();
        JsonNode retrievedSubset = instance.getSubset(
                originalID,
                true,
                true,
                true)
                .getBody();

        assertNotNull(retrievedSubset);
        assertFalse(retrievedSubset.isEmpty());
        assertEquals(originalID, retrievedSubset.get(Field.ID).asText());
    }

    @Test
    void postSubsetAndCheckDateStampsOfResponse(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fCS1);
        instance.postSubsetSeries(subsetJsonNode);
        JsonNode retrievedSubset = instance.getSubset(
                subsetJsonNode.get(Field.ID).asText(),
                true,
                true,
                true)
                .getBody();
        assertTrue(retrievedSubset.has(Field.CREATED_DATE));
        assertTrue(retrievedSubset.has(Field.LAST_UPDATED_DATE));
    }

    @Test
    void postVersionValidFromDifferentFromValidFrom(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_1);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, postRE.getStatusCode());
    }

    @Test
    void postVersionValidFromDifferentFromValidFrom2(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_2);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, postRE.getStatusCode());
    }

    @Test
    void putVersionValidFromDifferentFromValidFrom(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_9);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());


        JsonNode subsetJsonNode2 = getSubset(fv0_1);
        String id = subsetJsonNode2.get(Field.ID).asText();
        ResponseEntity<JsonNode> putRE = instance.putSubset(id, subsetJsonNode2);

        assertEquals(HttpStatus.BAD_REQUEST, putRE.getStatusCode());
    }

    @Test
    void putValidFromBeforeFirstVersionValidFrom(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv1_0);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());


        JsonNode subsetJsonNode2 = getSubset(fv5_0);
        String id = subsetJsonNode2.get(Field.ID).asText();
        ResponseEntity<JsonNode> putRE = instance.putSubset(id, subsetJsonNode2);

        assertEquals(HttpStatus.BAD_REQUEST, putRE.getStatusCode());
    }

    @Test
    void postOpenThenPutDraft(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv1_0);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());


        JsonNode subsetJsonNode2 = getSubset(fv0_9);
        String id = subsetJsonNode2.get(Field.ID).asText();
        ResponseEntity<JsonNode> putRE = instance.putSubset(id, subsetJsonNode2);

        assertEquals(HttpStatus.BAD_REQUEST, putRE.getStatusCode());
    }

    @Test
    void postVersionValidUntilDifferentFromValidUntil(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_4);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        // posted versionValidUntil should be ignored by the server because it is generated automatically
        // This means the subset should be created even if versionValidFrom is wrong
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());
    }

    @Test
    void postVersionValidUntilDifferentFromValidUntil2(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_5);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        // posted versionValidUntil should be ignored by the server because it is generated automatically
        // This means the subset should be created even if versionValidFrom is wrong
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());
    }

    @Test
    void postOpenThenPutDifferentValidUntil(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv1_5);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());


        JsonNode subsetJsonNode2 = getSubset(fv2_2);
        String id = subsetJsonNode2.get(Field.ID).asText();
        ResponseEntity<JsonNode> putRE = instance.putSubset(id, subsetJsonNode2);

        assertEquals(HttpStatus.OK, putRE.getStatusCode());
        ResponseEntity<JsonNode> versions = instance.getVersions(
                subsetJsonNode.get(Field.ID).asText(),
                true,
                false,
                true);
        ArrayNode versionsArr = (ArrayNode) versions.getBody();
        for (JsonNode version : versionsArr){

            assertTrue(!version.has(Field.VALID_UNTIL) || version.get(Field.VALID_UNTIL).isNull() || version.get(Field.VALID_UNTIL).asText().isBlank());
        }
    }

    @Test
    void postInvalidID(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode1 = getSubset(fInvalidID);

        ResponseEntity<JsonNode> postRE1 = instance.postSubsetSeries(subsetJsonNode1);
        assertEquals(HttpStatus.BAD_REQUEST, postRE1.getStatusCode());
    }

    @Test
    void postSameSubsetIDTwice(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode1 = getSubset(fv1_0);
        JsonNode subsetJsonNode2 = getSubset(fv1_0);

        ResponseEntity<JsonNode> postRE1 = instance.postSubsetSeries(subsetJsonNode1);
        assertEquals(HttpStatus.CREATED, postRE1.getStatusCode());
        ResponseEntity<JsonNode> postRE2 = instance.postSubsetSeries(subsetJsonNode2);
        assertEquals(HttpStatus.BAD_REQUEST, postRE2.getStatusCode());
    }

    @Test
    void postMissingVersion(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_3);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, postRE.getStatusCode());
    }

    @Test
    void postDraftSubset(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_9);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());
    }

    @Test
    void putDraftSubsetWhenSubsetDoesNotExist(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_9);
        String id = subsetJsonNode.get(Field.ID).asText();
        ResponseEntity<JsonNode> putRE = instance.putSubset(id, subsetJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, putRE.getStatusCode());
    }

    @Test
    void postDraftSubsetThenPutAndCheckLastUpdatedDate(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_9);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());
        String lastUpdated1 = postRE.getBody().get(Field.LAST_UPDATED_DATE).asText();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode subsetJsonNode2 = getSubset(fv1_0);
        String id = subsetJsonNode.get(Field.ID).asText();
        ResponseEntity<JsonNode> putRE = instance.putSubset(id, subsetJsonNode2);
        assertEquals(HttpStatus.OK, putRE.getStatusCode());
        String lastUpdated2 = putRE.getBody().get(Field.LAST_UPDATED_DATE).asText();
        System.out.println("lastUpdatedDate1: "+lastUpdated1);
        System.out.println("lastUpdatedDate2: "+lastUpdated2);
        assertTrue(lastUpdated1.compareTo(lastUpdated2) < 0);
    }

    @Test
    void postDraftSubsetThenPutWrongIDVersion(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode subsetJsonNode = getSubset(fv0_9);
        ResponseEntity<JsonNode> postRE = instance.postSubsetSeries(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());

        JsonNode subsetJsonNode2 = getSubset(fv1_0);
        ResponseEntity<JsonNode> putRE = instance.putSubset("wrong-id", subsetJsonNode2);
        assertEquals(HttpStatus.BAD_REQUEST, putRE.getStatusCode());
    }

    @Test
    void postDraftThenPutOpenExpectingStatusCreatedAndOK(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode draft = getSubset(fv0_9);
        ResponseEntity<JsonNode> postDraftRE = instance.postSubsetSeries(draft);
        assertEquals(HttpStatus.CREATED, postDraftRE.getStatusCode());

        JsonNode open = getSubset(fv1_0);
        ResponseEntity<JsonNode> putOpenRE = instance.putSubset(open.get(Field.ID).asText(), open);
        assertEquals(HttpStatus.OK, putOpenRE.getStatusCode());
    }

    @Test
    void testIncludeDraftsParameter(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode draft = getSubset(fv0_9);
        String id = draft.get(Field.ID).asText();
        instance.postSubsetSeries(draft);

        ResponseEntity<JsonNode> getSubsetsNoDraftRE = instance.getSubset(
                id,
                false,
                false,
                true);
        assertEquals(HttpStatus.NOT_FOUND, getSubsetsNoDraftRE.getStatusCode());
        ResponseEntity<JsonNode> getSubsetsWithDraftsRE = instance.getSubset(
                id,
                true,
                true,
                true);
        assertEquals(HttpStatus.OK, getSubsetsWithDraftsRE.getStatusCode());

        JsonNode open = getSubset(fv1_0);
        instance.putSubset(id, open);
        getSubsetsNoDraftRE = instance.getSubset(
                id,
                false,
                false,
                true);
        assertEquals(HttpStatus.OK, getSubsetsNoDraftRE.getStatusCode());
    }

    @Test
    void putNewVersion(){
        JsonNode subsetv1 = getSubset(fv1_0);
        JsonNode subsetv3 = getSubset(fv3_0);

        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        instance.postSubsetSeries(subsetv1);
        ResponseEntity<JsonNode> putRE = instance.putSubset(subsetv3.get(Field.ID).asText(), subsetv3);
        assertEquals(HttpStatus.OK, putRE.getStatusCode());
    }

    @Test
    void putNewVersionDraftWhenAnotherDraftAlreadyExists(){
        JsonNode subsetv1 = getSubset(fv1_0);
        JsonNode subsetv2 = getSubset(fv2_0);
        JsonNode subsetv3 = getSubset(fv3_0);

        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        instance.postSubsetSeries(subsetv1);
        ResponseEntity<JsonNode> putRE2 = instance.putSubset(subsetv3.get(Field.ID).asText(), subsetv2);
        ResponseEntity<JsonNode> putRE3 = instance.putSubset(subsetv3.get(Field.ID).asText(), subsetv3);

        assertEquals(HttpStatus.OK, putRE2.getStatusCode());
        assertEquals(HttpStatus.OK, putRE3.getStatusCode());
    }

    @Test
    void putNewVersionWithSameVersionValidFromDate(){
        JsonNode subsetv1 = getSubset(fv1_0);
        JsonNode subsetv3 = getSubset(fv3_1);

        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        instance.postSubsetSeries(subsetv1);
        ResponseEntity<JsonNode> putRE = instance.putSubset(subsetv3.get(Field.ID).asText(), subsetv3);
        assertEquals(HttpStatus.BAD_REQUEST, putRE.getStatusCode());
    }

    @Test
    void putPatchWithChangesToDRAFT(){
        JsonNode subsetv1 = getSubset(fv0_9);
        JsonNode subsetv2 = getSubset(fv0_91);

        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        instance.postSubsetSeries(subsetv1);
        ResponseEntity<JsonNode> putRE = instance.putSubset(subsetv2.get(Field.ID).asText(), subsetv2);
        assertEquals(HttpStatus.OK, putRE.getStatusCode());
    }

    @Test
    void putPatchWithChangesToOPEN(){
        JsonNode subsetv1 = getSubset(fv1_0);
        JsonNode subsetv2 = getSubset(fv1_4);

        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        instance.postSubsetSeries(subsetv1);
        ResponseEntity<JsonNode> putRE = instance.putSubset(subsetv2.get(Field.ID).asText(), subsetv2);
        assertEquals(HttpStatus.OK, putRE.getStatusCode());
    }

    @Test
    void putNewOpenVersionWithChanges(){
        JsonNode subsetv1 = getSubset(fv1_0);
        JsonNode subsetv2 = getSubset(fv2_1);

        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        instance.postSubsetSeries(subsetv1);
        ResponseEntity<JsonNode> putRE = instance.putSubset(subsetv2.get(Field.ID).asText(), subsetv2);
        assertEquals(HttpStatus.OK, putRE.getStatusCode());
    }

    @Test
    void testNameChangeAffectingPreviousVersions(){
        JsonNode subsetV1 = getSubset(fv1_0);
        String nameV1 = subsetV1.get(Field.NAME).get(0).get("languageText").asText();
        JsonNode subsetV2 = getSubset(fv2_1);
        String nameV2 = subsetV2.get(Field.NAME).get(0).get("languageText").asText();
        assertNotEquals(nameV1, nameV2);

        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        ResponseEntity<JsonNode> postV1RE = instance.postSubsetSeries(subsetV1);
        JsonNode postV1REBody = postV1RE.getBody();
        String v1String = postV1REBody.get(Field.VERSION).asText();
        String id = postV1REBody.get(Field.ID).asText();
        ResponseEntity<JsonNode> putV2RE = instance.putSubset(id, subsetV2);
        assertEquals(HttpStatus.OK, putV2RE.getStatusCode());
        ResponseEntity<JsonNode> getV1RE = instance.getVersion(
                id,
                v1String,
                true,
                false,
                false);
        String nameV1AfterV2Put = getV1RE.getBody().get(Field.NAME).get(0).get("languageText").asText();
        assertEquals(nameV2, nameV1AfterV2Put);
        System.out.println("v1 name: "+nameV1);
        System.out.println("v2 name: "+nameV2);
        System.out.println("v1 name after PUTing v2: "+nameV1AfterV2Put);

    }

    @Test
    void getSubsetsCheckStatusOK() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> subsets = instance.getSubsets(
                true,
                true,
                false);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
    }

    @Test
    void getSubsetsUrnOnlyCheckStatusOK() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        assertNotNull(instance);

        ResponseEntity<JsonNode> subsets = instance.getSubsets(
                true,
                true,
                true);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
    }

    @Test
    void getSubsetsUrnOnlyCheckContainsURNAndRank() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        assertNotNull(instance);
        instance.deleteAllSeries();

        instance.postSubsetSeries(getSubset(fv1_0));
        JsonNode v3 = getSubset(fv3_0);
        String id = v3.get(Field.ID).asText();
        instance.putSubset(id, v3);

        ResponseEntity<JsonNode> subsets = instance.getSubsets(
                true,
                true,
                true);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
        JsonNode body = subsets.getBody();
        assertNotNull(body);
        assertTrue(body.isArray());
        ArrayNode bodyArrayNode = (ArrayNode) body;
        System.out.println("Size of list of subsets: "+bodyArrayNode.size());
        assertEquals(1, bodyArrayNode.size());
        for (JsonNode subsetJsonNode : bodyArrayNode) {
            JsonNode codesList = subsetJsonNode.get(Field.CODES);
            assertTrue(codesList.isArray());
            ArrayNode codesListArrayNode = (ArrayNode) codesList;
            for (JsonNode rankedURN : codesListArrayNode) {
                assertTrue(rankedURN.has(Field.URN));
                assertTrue(rankedURN.has(Field.RANK));
            }
        }
    }

    @Test
    void getAllSubsetsCheckStatusOK() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        ResponseEntity<JsonNode> response = instance.getSubsets(
                true,
                true,
                false);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getAllSubsetsCheckResponseBodyNotNull() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        ResponseEntity<JsonNode> response = instance.getSubsets(
                true,
                true,
                false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        assertNotNull(body);
    }

    @Test
    void getAllSubsetsCheckBodyIsArray() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        ResponseEntity<JsonNode> response = instance.getSubsets(
                true,
                true,
                false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        assertTrue(body.isArray());

        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getIllegalIdSubset() {
        ResponseEntity<JsonNode> response = SubsetsControllerV2.getInstance().getSubset(
                "this-id-is-not-legal-¤%&#!§|`^¨~'*=)(/\\£$@{[]}",
                false,
                false,
                false);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getNonExistingSubset() {
        ResponseEntity<JsonNode> response = SubsetsControllerV2.getInstance().getSubset(
                "this-id-does-not-exist",
                true,
                true,
                true);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getNonExistentSubsetVersions() {
        ResponseEntity<JsonNode> response = SubsetsControllerV2.getInstance().getVersions(
                "this-id-does-not-exist",
                true,
                true,
                true);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getAllIndividualSubsetsCompareIDs() {
        ResponseEntity<JsonNode> response = SubsetsControllerV2.getInstance().getSubsets(
                true,
                true,
                false);

        System.out.println("All subsets:");
        JsonNode responseBody = response.getBody();
        System.out.println(responseBody);
        assertNotNull(responseBody);
        assertTrue(responseBody.isArray());
        System.out.println("IDs:");
        for (JsonNode jsonNode : responseBody) {
            String id = jsonNode.get(Field.ID).asText();
            JsonNode subset = SubsetsControllerV2.getInstance().getSubset(
                    id,
                    true,
                    true,
                    false)
                    .getBody();
            assert subset != null : "getSubset "+id+" did not return any result";
            assertTrue(subset.has(Field.ID));
            assertEquals(subset.get(Field.ID).asText(), jsonNode.get(Field.ID).asText());
            System.out.println(subset.get(Field.ID));
        }
    }

    @Test
    void getAllVersionsOfAllSubsets() {
        ResponseEntity<JsonNode> response = SubsetsControllerV2.getInstance().getSubsets(
                true,
                true,
                false);

        System.out.println("All subsets:");
        JsonNode body = response.getBody();
        assertNotNull(body);
        System.out.println(body);
        System.out.println("IDs:");
        for (JsonNode jsonNode : response.getBody()) {
            JsonNode subset = SubsetsControllerV2.getInstance().getSubset(
                    jsonNode.get("id").asText(),
                    true,
                    true,
                    false)
                    .getBody();
            assertNotNull(subset);
            assertTrue(subset.has(Field.ID));
            assertEquals(subset.get(Field.ID).asText(), jsonNode.get(Field.ID).asText());
            System.out.println("ID: "+subset.get(Field.ID));

            ArrayNode versions = (ArrayNode) SubsetsControllerV2.getInstance().getVersions(
                    subset.get(Field.ID).asText(),
                    true,
                    true,
                    false)
                    .getBody();
            assertNotNull(versions);
            assertNotEquals(0, versions.size());
            for (JsonNode version : versions) {
                System.out.println("Version: "+version.get(
                        Field.VERSION).asText()
                        +" admin status: "
                        +version.get(Field.ADMINISTRATIVE_STATUS).asText()
                        +" name: "
                        +version.get(Field.NAME).get(0).get("languageText").asText());
            }
        }
    }
    */
}