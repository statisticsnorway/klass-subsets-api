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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubsetsControllerV2Test {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsServiceApplicationTests.class);
    private final File series_1_0 = new File("src/test/resources/series_examples/series_1_0.json");
    private final File series_1_0_invalid_id = new File("src/test/resources/series_examples/series_1_0_invalid_id.json");
    private final File series_1_1 = new File("src/test/resources/series_examples/series_1_1.json"); // A legal edit of series_1_0

    private final File version_1_0_1 = new File("src/test/resources/version_examples/version_1_0_1.json");
    private final File version_1_0_1_nocodes_draft = new File("src/test/resources/version_examples/version_1_0_1_nocodes_draft.json");
    private final File version_1_0_1_nocodes_open = new File("src/test/resources/version_examples/version_1_0_1_nocodes_open.json");
    private final File version_1_0_1_1 = new File("src/test/resources/version_examples/version_1_0_1_1.json"); // A legal edit of version_1_0_1
    private final File version_1_0_1_open = new File("src/test/resources/version_examples/version_1_0_1_open.json");

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
    void postDraftThenPutOpenExpectingStatusCreatedAndOK(){
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

        JsonNode versionOpen = readJsonFile(version_1_0_1_open);
        ResponseEntity<JsonNode> putOpenVersionRE = instance.putSubsetVersion(seriesId, "1", versionOpen);
        assertTrue(putOpenVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.OK, putOpenVersionRE.getStatusCode());
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
            Thread.sleep(100); //To make sure the resource is available from LDS before we GET it
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

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode version2 = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersion2RE = instance.postSubsetVersion(seriesId, version2);
        assertTrue(postVersion2RE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersion2RE.getStatusCode());

        ResponseEntity<JsonNode> getVersionByIdShouldNotExistRE = instance.getVersion(seriesId, "1");
        assertEquals(HttpStatus.NOT_FOUND, getVersionByIdShouldNotExistRE.getStatusCode());

        ResponseEntity<JsonNode> getVersion2ByIdShouldExistRE = instance.getVersion(seriesId, "2");
        assertEquals(HttpStatus.OK, getVersion2ByIdShouldExistRE.getStatusCode());
    }

    @Test
    void postSubsetAndCheckAutoGeneratedFieldsOfResponse(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        instance.postSubsetSeries(seriesJsonNode);
        String seriesID = seriesJsonNode.get(Field.ID).asText();

        JsonNode versionJsonNode = readJsonFile(version_1_0_1);
        instance.postSubsetVersion(seriesID, versionJsonNode);
        JsonNode retrievedSubsetSeries = instance.getSubsetSeriesByID(seriesID, false).getBody();
        assertTrue(retrievedSubsetSeries.has(Field.LAST_MODIFIED));

        JsonNode retrievedSubsetVersion = instance.getVersion(seriesID, "1").getBody();
        assertTrue(retrievedSubsetVersion.has(Field.LAST_MODIFIED));
        assertTrue(retrievedSubsetVersion.has(Field.CREATED_DATE));
        assertTrue(retrievedSubsetVersion.has(Field.SERIES_ID));
        assertTrue(retrievedSubsetVersion.has(Field.VERSION));
    }


    @Test
    void postOpenThenPutDraft(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        String seriesID = seriesJsonNode.get(Field.ID).asText();
        instance.postSubsetSeries(seriesJsonNode);

        JsonNode versionOpenJsonNode = readJsonFile(version_1_0_1_open);
        instance.postSubsetVersion(seriesID, versionOpenJsonNode);

        JsonNode versionDraftJsonNode = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> putDraftAfterOpenRE = instance.putSubsetVersion(seriesID, "1", versionDraftJsonNode);

        assertEquals(HttpStatus.BAD_REQUEST, putDraftAfterOpenRE.getStatusCode());
    }

    @Test
    void postSubsetSeriesAndCheckIDOfResponse(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        String seriesId = seriesJsonNode.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(seriesJsonNode);
        JsonNode getSeriesJsonNode = postSeriesRE.getBody();

        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());
        assertEquals(seriesJsonNode.get(Field.ID).asText(), getSeriesJsonNode.get(Field.ID).asText());
        System.out.println(postSeriesRE.getBody().toPrettyString());
        JsonNode retrievedSubset = instance.getSubsetSeriesByID(seriesId, false).getBody();

        assertNotNull(retrievedSubset);
        assertFalse(retrievedSubset.isEmpty());
        assertEquals(seriesId, retrievedSubset.get(Field.ID).asText());
    }

    @Test
    void postInvalidID(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode seriesJsonNode = readJsonFile(series_1_0_invalid_id);
        String seriesId = seriesJsonNode.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(seriesJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, postSeriesRE.getStatusCode());
        System.out.println("Supposedly invalid series ID: "+seriesId);
    }

    @Test
    void postSameSubsetIDTwice(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        JsonNode seriesJsonNode2 = readJsonFile(series_1_0);
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(seriesJsonNode);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());
        ResponseEntity<JsonNode> postSeriesRE2 = instance.postSubsetSeries(seriesJsonNode2);
        assertEquals(HttpStatus.BAD_REQUEST, postSeriesRE2.getStatusCode());
    }

    @Test
    void putSeriesWhenItHasNotBeenPosted(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        String seriesId = seriesJsonNode.get(Field.ID).asText();
        ResponseEntity<JsonNode> putSeriesRE = instance.putSubsetSeries(seriesId, seriesJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, putSeriesRE.getStatusCode());
    }

    @Test
    void putVersionWhenItHasNotBeenPosted(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        String seriesID = seriesJsonNode.get(Field.ID).asText();
        instance.postSubsetSeries(seriesJsonNode);

        JsonNode versionOpenJsonNode = readJsonFile(version_1_0_1_open);
        ResponseEntity<JsonNode> putVersionRE = instance.putSubsetVersion(seriesID, "1", versionOpenJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, putVersionRE.getStatusCode());
    }

    @Test
    void postDraftSubsetThenPutAndCheckLastModified(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        String seriesID = seriesJsonNode.get(Field.ID).asText();
        instance.postSubsetSeries(seriesJsonNode);

        JsonNode versionDraftJsonNode = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postDraftVersionRE = instance.postSubsetVersion(seriesID, versionDraftJsonNode);
        String lastMod1 = postDraftVersionRE.getBody().get(Field.LAST_MODIFIED).asText();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ResponseEntity<JsonNode> getVersionRE1 = instance.getVersion(seriesID, "1");
        String lastMod1Get = getVersionRE1.getBody().get(Field.LAST_MODIFIED).asText();
        assertEquals(lastMod1, lastMod1Get);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode versionOpenJsonNode = readJsonFile(version_1_0_1_open);
        ResponseEntity<JsonNode> putOpenVersionRE = instance.putSubsetVersion(seriesID, "1", versionOpenJsonNode);
        assertEquals(HttpStatus.OK, putOpenVersionRE.getStatusCode());
        String lastMod2 = putOpenVersionRE.getBody().get(Field.LAST_MODIFIED).asText();

        System.out.println("lastModified 1: "+lastMod1);
        System.out.println("lastModified 2: "+lastMod2);
        assertTrue(lastMod1.compareTo(lastMod2) < 0);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ResponseEntity<JsonNode> getVersionRE2 = instance.getVersion(seriesID, "1");
        String lastMod2Get = getVersionRE2.getBody().get(Field.LAST_MODIFIED).asText();
        assertEquals(lastMod2, lastMod2Get);
    }


    @Test
    void postNewVersion(){
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

        // DANGER: POSTing two versions right after each other often leads to overwriting the first version.
        // Therefore we sleep for a bit
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            fail("Sleep failed");
        }

        JsonNode version1_0_1_1 = readJsonFile(version_1_0_1_1);
        ResponseEntity<JsonNode> putVersionRE = instance.postSubsetVersion(seriesId, version1_0_1_1);
        assertEquals(HttpStatus.CREATED, putVersionRE.getStatusCode());

        try {
            Thread.sleep(50); //To make sure the resource is available from LDS before we GET it
        } catch (InterruptedException e) {
            fail("Sleep failed");
        }

        ResponseEntity<JsonNode> getVersionsRE = instance.getVersions(seriesId, true, true);
        assertEquals(HttpStatus.OK, getVersionsRE.getStatusCode());
        JsonNode getVersionsBody = getVersionsRE.getBody();
        assertTrue(getVersionsBody.isArray());
        ArrayNode versionsArrayNode = (ArrayNode)getVersionsBody;
        System.out.println("*** VERSIONS ARRAY NODE ***");
        System.out.println(versionsArrayNode.toPrettyString());
        System.out.println();
        assertEquals(2, versionsArrayNode.size());
    }

    @Test
    void postNewOpenVersionWithSameVersionValidFromDateAndExpectBadRequestStatus(){
        //We should not be able to publish a version that has same validFrom date as an existing published version
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1_open);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> postVersionRE2 = instance.postSubsetVersion(seriesId, version);
        assertEquals(HttpStatus.BAD_REQUEST, postVersionRE2.getStatusCode());
    }

    @Test
    void getIllegalIdSubset() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        ResponseEntity<JsonNode> response = instance.getSubsetSeriesByID("this-id-is-not-legal-¤%&#!§|`^¨~'*=)(/\\£$@{[]}", false);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getNonExistingSubsetSeries() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        ResponseEntity<JsonNode> response = instance.getSubsetSeriesByID("this-id-does-not-exist", false);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }


    @Test
    void getNonExistentSubsetVersions() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        ResponseEntity<JsonNode> getVersionRE = instance.getVersion(seriesId, "1");


        System.out.println("STATUS CODE: ");
        System.out.println(getVersionRE.getStatusCodeValue());
        assertEquals(HttpStatus.NOT_FOUND, getVersionRE.getStatusCode());
    }

    /*


    @Test
    void putNewVersionWithVersionValidFromInValidityPeriodOfLastVersion(){
        //We should be able to save a draft that overlaps validity period of last published subset with open ended validity period.
        fail("NOT IMPLEMENTED");
    }

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
    */
}