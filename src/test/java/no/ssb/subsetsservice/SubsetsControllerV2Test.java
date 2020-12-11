package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.sql.SQLOutput;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubsetsControllerV2Test {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsServiceApplicationTests.class);
    private final File series_1_0 = new File("src/test/resources/series_examples/series_1_0.json");
    private final File series_1_0_invalid_id = new File("src/test/resources/series_examples/series_1_0_invalid_id.json");
    private final File series_1_1 = new File("src/test/resources/series_examples/series_1_1.json"); // A legal edit of series_1_0
    private final File series_2_0 = new File("src/test/resources/series_examples/series_2_0.json");

    private final File version_1_0_1 = new File("src/test/resources/version_examples/version_1_0_1.json");
    private final File version_1_0_1_nocodes_draft = new File("src/test/resources/version_examples/version_1_0_1_nocodes_draft.json");
    private final File version_1_0_1_nocodes_open = new File("src/test/resources/version_examples/version_1_0_1_nocodes_open.json");
    private final File version_1_0_1_1 = new File("src/test/resources/version_examples/version_1_0_1_1.json"); // A legal edit of version_1_0_1
    private final File version_1_0_1_open = new File("src/test/resources/version_examples/version_1_0_1_open.json");
    private final File version_2_0_1 = new File("src/test/resources/version_examples/version_2_0_1.json");
    private final File version_2_0_2 = new File("src/test/resources/version_examples/version_2_0_2.json");
    private final File version_2_0_2_validUntil = new File("src/test/resources/version_examples/version_2_0_2_validUntil.json");
    private final File version_2_0_3 = new File("src/test/resources/version_examples/version_2_0_3.json");
    private final File version_2_0_3_overlapping_date = new File("src/test/resources/version_examples/version_2_0_3_overlapping_date.json");

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
    public static void cleanUp() {
        System.out.println("After All cleanUp() method called");
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        instance.deleteAllSeries();
    }

    @BeforeEach
    public void deleteAllSeries() {
        SubsetsControllerV2.getInstance().deleteAllSeries();
    }

    @Test
    void postSubsetSeries() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        JsonNode series = readJsonFile(series_1_0);
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());
    }

    @Test
    void getAllSubsetSeries() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
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
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        //TODO: Make sure all the right fields have been added to the version

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getVersionsRE = instance.getVersions(seriesId, true, true);
        assertEquals(getVersionsRE.getBody().size(), 1);
    }

    @Test
    void checkNorwegianLetters() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getSeriesRE = instance.getSubsetSeriesByID(seriesId, false);

        ArrayNode originalDescriptionArrayNode = series.get("description").deepCopy();
        String originalNbDescription = "";
        for (JsonNode languageTextObject : originalDescriptionArrayNode)
            if (languageTextObject.get("languageCode").asText().equals("nb"))
                originalNbDescription = languageTextObject.get("languageText").asText();

        ArrayNode newDescriptionArrayNode = getSeriesRE.getBody().get("description").deepCopy();
        String newNbDescription = "";
        for (JsonNode languageTextObject : newDescriptionArrayNode)
            if (languageTextObject.get("languageCode").asText().equals("nb"))
                newNbDescription = languageTextObject.get("languageText").asText();

        assertEquals(originalNbDescription, newNbDescription);

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getVersionsRE = instance.getVersions(seriesId, true, true);

        ArrayNode originalRationaleArrayNode = version.get("versionRationale").deepCopy();
        String originalNbRationale = "";
        for (JsonNode languageTextObject : originalRationaleArrayNode)
            if (languageTextObject.get("languageCode").asText().equals("nb"))
                originalNbRationale = languageTextObject.get("languageText").asText();

        ArrayNode newRationaleArrayNode = getVersionsRE.getBody().get(0).get("versionRationale").deepCopy();
        String newNbRationale = "";
        for (JsonNode languageTextObject : newRationaleArrayNode)
            if (languageTextObject.get("languageCode").asText().equals("nb"))
                newNbRationale = languageTextObject.get("languageText").asText();

        assertEquals(originalNbRationale, newNbRationale);

    }


    @Test
    void postDraftThenPutOpenExpectingStatusCreatedAndOK() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        String versionUID = postVersionRE.getBody().get(Field.VERSION_ID).asText();

        JsonNode versionOpen = readJsonFile(version_1_0_1_open);
        ResponseEntity<JsonNode> putOpenVersionRE = instance.putSubsetVersion(seriesId, versionUID, versionOpen);
        assertTrue(putOpenVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.OK, putOpenVersionRE.getStatusCode());
    }


    @Test
    void getSubsetVersions() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

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
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        String versionUID1 = postVersionRE.getBody().get(Field.VERSION_ID).asText();

        ResponseEntity<JsonNode> getVersionNrRE = instance.getVersion(seriesId, versionUID1);
        assertEquals(HttpStatus.OK, getVersionNrRE.getStatusCode());
        JsonNode body1 = getVersionNrRE.getBody();
        assertNotNull(body1);

        ResponseEntity<JsonNode> getVersionFullUuidRE = instance.getVersion(seriesId, seriesId+"_"+versionUID1);
        assertEquals(HttpStatus.OK, getVersionFullUuidRE.getStatusCode());
        JsonNode body2 = getVersionFullUuidRE.getBody();
        assertNotNull(body2);
    }

    @Test
    void putSubsetVersionDraftChangingAllFieldsExceptValidFrom() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        String versionUID1 = postVersionRE.getBody().get(Field.VERSION_ID).asText();

        JsonNode version1_0_1_1 = readJsonFile(version_1_0_1_1);
        ResponseEntity<JsonNode> putVersionRE = instance.putSubsetVersion(seriesId, versionUID1, version1_0_1_1);
        assertEquals(HttpStatus.OK, putVersionRE.getStatusCode());

        ResponseEntity<JsonNode> getVersionRE = instance.getVersion(seriesId, versionUID1);
        JsonNode body = getVersionRE.getBody();
        assertNotNull(body);
        assertTrue(body.has(Field.CODES));
        assertTrue(body.get(Field.CODES).isArray());
        ArrayNode codesArray = (ArrayNode) body.get(Field.CODES);
        assertEquals(2, codesArray.size());
    }

    @Test
    void postDraftNoCodesThenPutOpenNoCodesExpectingBadRequestResponse() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode versionNoCodesDraft = readJsonFile(version_1_0_1_nocodes_draft);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, versionNoCodesDraft);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        String versionUID1 = postVersionRE.getBody().get(Field.VERSION_ID).asText();

        JsonNode versionNoCodesOpen = readJsonFile(version_1_0_1_nocodes_open);
        ResponseEntity<JsonNode> putResponseEntity = instance.putSubsetVersion(seriesId, versionUID1, versionNoCodesOpen);
        assertEquals(HttpStatus.BAD_REQUEST, putResponseEntity.getStatusCode()); // 0 codes is not allowed in published subset
    }

    @Test
    void postOpenNoCodesExpectingBadRequestResponse(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
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

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        String versionUID1 = postVersionRE.getBody().get(Field.VERSION_ID).asText();

        ResponseEntity<JsonNode> getVersionByIdRE = instance.getVersion(seriesId, versionUID1);
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

        ResponseEntity<JsonNode> getVersionByIdShouldNotExistRE = instance.getVersion(seriesId, versionUID1);
        assertEquals(HttpStatus.NOT_FOUND, getVersionByIdShouldNotExistRE.getStatusCode());

    }

    @Test
    void deleteSeriesByIdThenCheckThatEmpty() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        String versionUID1 = postVersionRE.getBody().get(Field.VERSION_ID).asText();

        ResponseEntity<JsonNode> getVersionByIdRE = instance.getVersion(seriesId, versionUID1);
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

        ResponseEntity<JsonNode> getVersionByIdShouldNotExistRE = instance.getVersion(seriesId, versionUID1);
        assertEquals(HttpStatus.NOT_FOUND, getVersionByIdShouldNotExistRE.getStatusCode());
    }


    @Test
    void deleteVersionByIdPostAnotherOneThenGetByIdToMakeSureTheIdIsDistinct(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        String versionUID1 = postVersionRE.getBody().get(Field.VERSION_ID).asText();

        try {
            Thread.sleep(50); // To make sure creation is completed before deletion is attempted
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        instance.deleteVersionById(seriesId, versionUID1);

        try {
            Thread.sleep(50); // To make sure deletion is completed before posting is attempted
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode version2 = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersion2RE = instance.postSubsetVersion(seriesId, version2);
        assertTrue(postVersion2RE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersion2RE.getStatusCode());
        String versionUID2 = postVersion2RE.getBody().get(Field.VERSION_ID).asText();

        ResponseEntity<JsonNode> getVersionByIdShouldNotExistRE = instance.getVersion(seriesId, versionUID1);
        assertEquals(HttpStatus.NOT_FOUND, getVersionByIdShouldNotExistRE.getStatusCode());

        ResponseEntity<JsonNode> getVersion2ByIdShouldExistRE = instance.getVersion(seriesId, versionUID2);
        assertEquals(HttpStatus.OK, getVersion2ByIdShouldExistRE.getStatusCode());
    }

    /*
    @Test
    void postDeleteTimeTest(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version);
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());
        String versionUID1 = postVersionRE.getBody().get(Field.VERSION).asText();

        instance.deleteVersionById(seriesId, versionUID1);

        long startTime = System.currentTimeMillis();
        long increment = 2000;

        ResponseEntity<JsonNode> getSeriesRE = instance.getSubsetSeriesByID(seriesId, false);
        ArrayNode versionsArray = null;
        if (getSeriesRE.getStatusCode().is2xxSuccessful())
            versionsArray = getSeriesRE.getBody().get(Field.VERSIONS).deepCopy();
        while (!getSeriesRE.getStatusCode().is2xxSuccessful() || versionsArray.size() > 0){
            try {
                Thread.sleep(increment);
                getSeriesRE = instance.getSubsetSeriesByID(seriesId, false);
                if (getSeriesRE.getStatusCode().is2xxSuccessful()) {
                    versionsArray = getSeriesRE.getBody().get(Field.VERSIONS).deepCopy();
                    System.out.println("*** VERSIONS ARRAY ***");
                    System.out.println(versionsArray.toPrettyString());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("Duration millis: "+duration);
    }
    */

    @Test
    void postSubsetAndCheckAutoGeneratedFieldsOfResponse(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        instance.postSubsetSeries(seriesJsonNode);
        String seriesID = seriesJsonNode.get(Field.ID).asText();

        JsonNode versionJsonNode = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesID, versionJsonNode);
        String versionUID = postVersionRE.getBody().get(Field.VERSION_ID).asText();
        JsonNode retrievedSubsetSeries = instance.getSubsetSeriesByID(seriesID, false).getBody();
        assertTrue(retrievedSubsetSeries.has(Field.LAST_MODIFIED));

        JsonNode retrievedSubsetVersion = instance.getVersion(seriesID, versionUID).getBody();
        assertTrue(retrievedSubsetVersion.has(Field.LAST_MODIFIED));
        assertTrue(retrievedSubsetVersion.has(Field.CREATED_DATE));
        assertTrue(retrievedSubsetVersion.has(Field.SUBSET_ID));
        assertTrue(retrievedSubsetVersion.has(Field.VERSION_ID));
    }


    @Test
    void postOpenThenPutDraft(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        String seriesID = seriesJsonNode.get(Field.ID).asText();
        instance.postSubsetSeries(seriesJsonNode);

        JsonNode versionOpenJsonNode = readJsonFile(version_1_0_1_open);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesID, versionOpenJsonNode);
        String versionUID = postVersionRE.getBody().get(Field.VERSION_ID).asText();

        JsonNode versionDraftJsonNode = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> putDraftAfterOpenRE = instance.putSubsetVersion(seriesID, versionUID, versionDraftJsonNode);

        assertEquals(HttpStatus.BAD_REQUEST, putDraftAfterOpenRE.getStatusCode());
    }

    @Test
    void postSubsetSeriesAndCheckIDOfResponse(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

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

        JsonNode seriesJsonNode = readJsonFile(series_1_0_invalid_id);
        String seriesId = seriesJsonNode.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(seriesJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, postSeriesRE.getStatusCode());
        System.out.println("Supposedly invalid series ID: "+seriesId);
    }

    @Test
    void postSameSubsetIDTwice(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

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

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        String seriesId = seriesJsonNode.get(Field.ID).asText();
        ResponseEntity<JsonNode> putSeriesRE = instance.putSubsetSeries(seriesId, seriesJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, putSeriesRE.getStatusCode());
    }

    @Test
    void putVersionWhenItHasNotBeenPosted(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

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

        JsonNode seriesJsonNode = readJsonFile(series_1_0);
        String seriesID = seriesJsonNode.get(Field.ID).asText();
        instance.postSubsetSeries(seriesJsonNode);

        JsonNode versionDraftJsonNode = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postDraftVersionRE = instance.postSubsetVersion(seriesID, versionDraftJsonNode);
        String lastMod1 = postDraftVersionRE.getBody().get(Field.LAST_MODIFIED).asText();
        String versionUID = postDraftVersionRE.getBody().get(Field.VERSION_ID).asText();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ResponseEntity<JsonNode> getVersionRE1 = instance.getVersion(seriesID, versionUID);
        String lastMod1Get = getVersionRE1.getBody().get(Field.LAST_MODIFIED).asText();
        assertEquals(lastMod1, lastMod1Get);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode versionOpenJsonNode = readJsonFile(version_1_0_1_open);
        ResponseEntity<JsonNode> putOpenVersionRE = instance.putSubsetVersion(seriesID, versionUID, versionOpenJsonNode);
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
        ResponseEntity<JsonNode> getVersionRE2 = instance.getVersion(seriesID, versionUID);
        String lastMod2Get = getVersionRE2.getBody().get(Field.LAST_MODIFIED).asText();
        assertEquals(lastMod2, lastMod2Get);
    }


    @Test
    void postNewVersion(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
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
    void postNewVersionThatSetsValidUntilOfPreviousVersionToOneDayBeforeValidFromOfNewVersion(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version_2_0_2 = readJsonFile(this.version_2_0_2);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version_2_0_2);
        String version202_uid = postVersionRE.getBody().get(Field.VERSION).asText();
        assertTrue(postVersionRE.getStatusCode().is2xxSuccessful());
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        // DANGER: POSTing two versions right after each other often leads to overwriting the first version.
        // Therefore we sleep for a bit
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            fail("Sleep failed");
        }

        JsonNode version_2_0_3 = readJsonFile(this.version_2_0_3);
        ResponseEntity<JsonNode> putVersionRE = instance.postSubsetVersion(seriesId, version_2_0_3);
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

        JsonNode version202AfterUpdate = instance.getVersion(seriesId, version202_uid).getBody();
        assertNotNull(version202AfterUpdate);
        assertTrue(version202AfterUpdate.has(Field.VALID_UNTIL));
        String validUntilAfterUpdate = version202AfterUpdate.get(Field.VALID_UNTIL).asText();
        System.out.println("version 202 validUntil after update: "+validUntilAfterUpdate);
        System.out.println("version 203 validFrom: "+version_2_0_3.get(Field.VALID_FROM).asText());
        assertTrue(validUntilAfterUpdate.compareTo(version_2_0_3.get(Field.VALID_FROM).asText()) == 0);
    }

    @Test
    void postNewOpenVersionWithSameVersionValidFromDateAndExpectBadRequestStatus(){
        //We should not be able to publish a version that has same validFrom date as an existing published version
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();
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

        ResponseEntity<JsonNode> response = instance.getSubsetSeriesByID("this-id-is-not-legal-¤%&#!§|`^¨~'*=)(/\\£$@{[]}", false);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getNonExistingSubsetSeries() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        ResponseEntity<JsonNode> response = instance.getSubsetSeriesByID("this-id-does-not-exist", false);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getNonExistentSubsetVersions() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        ResponseEntity<JsonNode> getVersionRE = instance.getVersion(seriesId, "1");

        System.out.println("STATUS CODE: ");
        System.out.println(getVersionRE.getStatusCodeValue());
        assertEquals(HttpStatus.NOT_FOUND, getVersionRE.getStatusCode());
    }

    @Test
    void putToAddValidUntilToOpenVersion(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_2_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version202 = readJsonFile(version_2_0_2);
        ResponseEntity<JsonNode> postVersion2RE = instance.postSubsetVersion(seriesId, version202);
        assertEquals(HttpStatus.CREATED, postVersion2RE.getStatusCode());
        String version2ID = postVersion2RE.getBody().get(Field.VERSION_ID).asText();

        JsonNode version202validUntil = readJsonFile(version_2_0_2_validUntil);
        ResponseEntity<JsonNode> putVersion2validUntilRE = instance.putSubsetVersion(seriesId, version2ID, version202validUntil);
        assertEquals(HttpStatus.OK, putVersion2validUntilRE.getStatusCode());
    }

    @Test
    void postNewVersionCollidingWithEndOfExistingVersion(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_2_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version201 = readJsonFile(version_2_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version201);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        JsonNode version202 = readJsonFile(version_2_0_2);
        ResponseEntity<JsonNode> postVersion2RE = instance.postSubsetVersion(seriesId, version202);
        assertEquals(HttpStatus.CREATED, postVersion2RE.getStatusCode());
        String version2ID = postVersion2RE.getBody().get(Field.VERSION_ID).asText();

        JsonNode version202validUntil = readJsonFile(version_2_0_2_validUntil);
        ResponseEntity<JsonNode> putVersion2validUntilRE = instance.putSubsetVersion(seriesId, version2ID, version202validUntil);
        if (!putVersion2validUntilRE.getStatusCode().is2xxSuccessful()) {
            System.out.println("*** BODY ***");
            System.out.println(putVersion2validUntilRE.getBody().toPrettyString());
        }
        assertEquals(HttpStatus.OK, putVersion2validUntilRE.getStatusCode());

        JsonNode version203_overlap = readJsonFile(version_2_0_3_overlapping_date);
        ResponseEntity<JsonNode> postVersion3RE = instance.postSubsetVersion(seriesId, version203_overlap);
        assertEquals(HttpStatus.BAD_REQUEST, postVersion3RE.getStatusCode());

        JsonNode version203_no_overlap = readJsonFile(version_2_0_3);
        postVersion3RE = instance.postSubsetVersion(seriesId, version203_no_overlap);
        assertEquals(HttpStatus.CREATED, postVersion3RE.getStatusCode());
    }

    @Test
    void postNewVersionCollidingWithBeginningOfExistingVersion(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_2_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version203_overlap = readJsonFile(version_2_0_3_overlapping_date);
        ResponseEntity<JsonNode> postVersion3RE = instance.postSubsetVersion(seriesId, version203_overlap);
        assertEquals(HttpStatus.CREATED, postVersion3RE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode version202validUntil = readJsonFile(version_2_0_2_validUntil);
        ResponseEntity<JsonNode> postVersion2validUntilRE = instance.postSubsetVersion(seriesId, version202validUntil);
        assertEquals(HttpStatus.BAD_REQUEST, postVersion2validUntilRE.getStatusCode());
    }

    @Test
    void testIncludeDraftsParameter() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode versionDraft = readJsonFile(version_1_0_1_nocodes_draft);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, versionDraft);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode versionOpen = readJsonFile(version_1_0_1_open);
        ResponseEntity<JsonNode> postVersionOpenRE = instance.postSubsetVersion(seriesId, versionOpen);
        assertEquals(HttpStatus.CREATED, postVersionOpenRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getVersionWithDraftsRE = instance.getVersions(seriesId, true, true);
        assertEquals(HttpStatus.OK, getVersionWithDraftsRE.getStatusCode());
        assertTrue(getVersionWithDraftsRE.hasBody());
        JsonNode body = getVersionWithDraftsRE.getBody();
        assertTrue(body.isArray());
        ArrayNode arrayNode = body.deepCopy();
        assertEquals(2, arrayNode.size());

        getVersionWithDraftsRE = instance.getVersions(seriesId, true, false);
        assertEquals(HttpStatus.OK, getVersionWithDraftsRE.getStatusCode());
        assertTrue(getVersionWithDraftsRE.hasBody());
        body = getVersionWithDraftsRE.getBody();
        assertTrue(body.isArray());
        arrayNode = body.deepCopy();
        assertEquals(1, arrayNode.size());
    }


    @Test
    void putNewVersionDraftWhenAnotherDraftAlreadyExists(){
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode versionDraft = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, versionDraft);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode versionDraft2 = readJsonFile(version_1_0_1_1);
        ResponseEntity<JsonNode> postVersionRE2 = instance.postSubsetVersion(seriesId, versionDraft2);
        assertEquals(HttpStatus.CREATED, postVersionRE2.getStatusCode());
    }

    @Test
    void getSubsetCodesAtDate() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode versionDraft = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, versionDraft);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getCodesAtRE = instance.getSubsetCodesAt(seriesId, "2020-11-11", true, true);
        assertEquals(HttpStatus.OK, getCodesAtRE.getStatusCode());
        ArrayNode codesArray = getCodesAtRE.getBody().deepCopy();
        assertEquals(1, codesArray.size());
    }

    @Test
    void getSubsetCodesAtDateBeforeValidityPeriod() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode versionDraft = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, versionDraft);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getCodesAtRE = instance.getSubsetCodesAt(seriesId, "2007-11-11", true, true);
        assertEquals(HttpStatus.OK, getCodesAtRE.getStatusCode());
        ArrayNode codesArray = getCodesAtRE.getBody().deepCopy();
        assertEquals(0, codesArray.size());
    }

    @Test
    void getSubsetCodesAtDateAfterValidityPeriod() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode versionDraft = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, versionDraft);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getCodesAtRE = instance.getSubsetCodesAt(seriesId, "2022-11-11", true, true);
        assertEquals(HttpStatus.OK, getCodesAtRE.getStatusCode());
        ArrayNode codesArray = getCodesAtRE.getBody().deepCopy();
        assertEquals(0, codesArray.size());
    }

    @Test
    void getSubsetCodesToday() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_2_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version201validUntil = readJsonFile(version_2_0_1);
        ResponseEntity<JsonNode> postVersion1validUntilRE = instance.postSubsetVersion(seriesId, version201validUntil);
        assertEquals(HttpStatus.CREATED, postVersion1validUntilRE.getStatusCode());

        JsonNode version202validUntil = readJsonFile(version_2_0_2);
        ResponseEntity<JsonNode> postVersion2validUntilRE = instance.postSubsetVersion(seriesId, version202validUntil);
        assertEquals(HttpStatus.CREATED, postVersion2validUntilRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getCodesTodayRE = instance.getSubsetCodes(seriesId, null, null, true, true);
        System.out.println("GET codes from a version that is valid today (codes need not be valid today, just the version) ");
        System.out.println(getCodesTodayRE.getBody().toPrettyString());
    }

    @Test
    void getSubsetCodesInDateRange() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_2_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version201validUntil = readJsonFile(version_2_0_1);
        ResponseEntity<JsonNode> postVersion1validUntilRE = instance.postSubsetVersion(seriesId, version201validUntil);
        assertEquals(HttpStatus.CREATED, postVersion1validUntilRE.getStatusCode());

        JsonNode version202validUntil = readJsonFile(version_2_0_2_validUntil);
        ResponseEntity<JsonNode> postVersion2validUntilRE = instance.postSubsetVersion(seriesId, version202validUntil);
        assertEquals(HttpStatus.CREATED, postVersion2validUntilRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getCodesFromToRE = instance.getSubsetCodes(seriesId, "2004-01-01", "2009-01-01", true, true);
        System.out.println();
        System.out.println(getCodesFromToRE.getBody().toPrettyString());
    }

    @Test
    void getSubsetCodesInDateRange2() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_1_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version201validUntil = readJsonFile(version_1_0_1);
        ResponseEntity<JsonNode> postVersion1validUntilRE = instance.postSubsetVersion(seriesId, version201validUntil);
        assertEquals(HttpStatus.CREATED, postVersion1validUntilRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResponseEntity<JsonNode> getCodesFromToRE = instance.getSubsetCodes(seriesId, "2020-01-01", "2022-01-01", true, true);
        System.out.println();
        System.out.println(getCodesFromToRE.getBody().toPrettyString());
        assertEquals(1, getCodesFromToRE.getBody().get(0).get(Field.CLASSIFICATION_VERSIONS).size());
    }

    /*
    @Test
    void validateVersion() {
    }

    @Test
    void validateSeries() {
    }
    */


    /*
    @Test
    void postNewVersionCreatingIllegalGapThenFillGapToMakeThatVersionLegal() {
        SubsetsControllerV2 instance = SubsetsControllerV2.getInstance();

        JsonNode series = readJsonFile(series_2_0);
        String seriesId = series.get(Field.ID).asText();
        ResponseEntity<JsonNode> postSeriesRE = instance.postSubsetSeries(series);
        assertEquals(HttpStatus.CREATED, postSeriesRE.getStatusCode());

        JsonNode version201 = readJsonFile(version_2_0_1);
        ResponseEntity<JsonNode> postVersionRE = instance.postSubsetVersion(seriesId, version201);
        assertEquals(HttpStatus.CREATED, postVersionRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        JsonNode version203 = readJsonFile(version_2_0_3);
        ResponseEntity<JsonNode> postVersion3RE = instance.postSubsetVersion(seriesId, version203);
        assertEquals(HttpStatus.BAD_REQUEST, postVersion3RE.getStatusCode());

        JsonNode version202validUntil = readJsonFile(version_2_0_2_validUntil);
        ResponseEntity<JsonNode> postVersion2validUntilRE = instance.postSubsetVersion(seriesId, version202validUntil);
        assertEquals(HttpStatus.CREATED, postVersion2validUntilRE.getStatusCode());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        postVersion3RE = instance.postSubsetVersion(seriesId, version203);
        assertEquals(HttpStatus.CREATED, postVersion3RE.getStatusCode());
    }
    */
}