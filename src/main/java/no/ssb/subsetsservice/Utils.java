package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

public class Utils {

    public static final String ISO_DATE_PATTERN = "yyyy-MM-dd";
    public static final String ISO_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"; // Quoted "Z" to indicate UTC, no timezone offset
    public static final String CLEAN_ID_REGEX = "^[a-zA-Z0-9-_]+$";
    public static final String YEAR_MONTH_DAY_REGEX = "([12]\\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01]))";
    public static final String VERSION_STRING_REGEX = "(\\d(\\.\\d)?(\\.\\d)?)";
    public static final String URN_FORMAT = "urn:ssb:klass-api:classifications:%s:code:%s";
    public static final String URN_FORMAT_ENCODED_NAME = "urn:ssb:klass-api:classifications:%s:code:%s:encodedName:%s";
    public static final String URN_FORMAT_VALID_FROM_ENCODED_NAME = "urn:ssb:klass-api:classifications:%s:code:%s:validFrom:%s:name:%s";
    public static final String SERIES_LINK_FORMAT = "/v2/subsets/%s";
    public static final String VERSION_LINK_FORMAT = SERIES_LINK_FORMAT+"/versions/%s";
    public static final String[] LANGUAGE_CODES = {"nb", "nn", "en"};

    public static boolean isYearMonthDay(String date){
        return date.matches(YEAR_MONTH_DAY_REGEX);
    }

    public static boolean isVersion(String version){
        return version.matches(VERSION_STRING_REGEX);
    }

    public static boolean isClean(String str){
        return str.matches(CLEAN_ID_REGEX);
    }

    public static JsonNode getSubsetVersionLinkNode(JsonNode subset) {
        String selfURN = getVersionLink(subset.get(Field.SUBSET_ID).asText(), subset.get(Field.VERSION_ID).asText());
        String seriesUrn = getSeriesLink(subset.get(Field.SUBSET_ID).asText());
        ObjectNode linksObject = getLinkSelfObject(selfURN);
        linksObject.set("series", getHrefNode(seriesUrn));
        return linksObject;
    }

    public static ObjectNode getHrefNode(String urn) {
        ObjectNode seriesNode = new ObjectMapper().createObjectNode();
        seriesNode.put("href", urn);
        return seriesNode;
    }

    public static ObjectNode getLinkSelfObject(String resourceUrn) {
        ObjectNode linksNode = new ObjectMapper().createObjectNode();
        linksNode.set("self", getHrefNode(resourceUrn));
        return linksNode;
    }

    public static String getVersionLink(String seriesUID, String versionID) {
        return String.format(VERSION_LINK_FORMAT, seriesUID, versionID);
    }

    public static String getSeriesLink(String seriesUID){
        return String.format(SERIES_LINK_FORMAT, seriesUID);
    }

    public static String getNowISO(){
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat(ISO_DATETIME_PATTERN);
        df.setTimeZone(tz);
        return df.format(new Date());
    }

    public static JsonNode cleanV1SubsetVersionField(JsonNode subset) {
        if (subset.isArray()){
            ArrayNode arrayNode = (ArrayNode) subset;
            return cleanV1SubsetArrayVersionFields(arrayNode);
        }
        ObjectNode clone = subset.deepCopy();
        if (!clone.has(Field.VERSION_ID)) {
            LoggerFactory.getLogger(Utils.class).error("subset did not contain 'version', so it could not be cleaned");
            return clone;
        }
        String oldVersion = clone.get(Field.VERSION_ID).asText();
        String majorVersion = oldVersion.split("\\.")[0];
        clone.put(Field.VERSION_ID, majorVersion);
        return clone;
    }

    private static ArrayNode cleanV1SubsetArrayVersionFields(ArrayNode subsetArray) {
        ArrayNode clone = subsetArray.deepCopy();
        for (int i = 0; i < subsetArray.size(); i++) {
            clone.set(i, cleanV1SubsetVersionField(clone.get(i)));
        }
        return clone;
    }

    public static boolean isNumeric(String string) {
        if (string == null)
            return false;

        try {
            Double.parseDouble(string);
            return true;
        } catch (NumberFormatException e){
            return false;
        }
    }

    public static boolean isInteger(String string) {
        if (isNumeric(string)){
            double d = Double.parseDouble(string);
            return (d % 1) == 0 && !Double.isInfinite(d);
        }
        return false;
    }

    public static String generateURN(String classification, String code) {
        return String.format(URN_FORMAT, classification, code);
    }

    public static ObjectNode createMultilingualText(String languageCode, String languageText) {
        ObjectNode mlT = new ObjectMapper().createObjectNode();
        mlT.put(Field.LANGUAGE_CODE, languageCode);
        mlT.put(Field.LANGUAGE_TEXT, languageText);
        return mlT;
    }

    public static ObjectNode addCodeVersionsToAllCodesInVersion(JsonNode subsetVersion, Logger LOG) {
        ObjectNode editableVersion = subsetVersion.deepCopy();
        LOG.debug("Finding out what classification versions the codes in the subsetVersion are used in");
        if (editableVersion.has(Field.CODES)){
            ArrayNode codesArrayNode = (ArrayNode)editableVersion.get(Field.CODES);
            for (int i = 0; i < codesArrayNode.size(); i++) {
                LOG.debug("Resolving code "+i+"/"+codesArrayNode.size());
                JsonNode code = Utils.addCodeVersions(codesArrayNode.get(i), LOG);
                if (code.get(Field.CLASSIFICATION_VERSIONS).size() < 1)
                    LOG.error("Code "+code.get(Field.CODE)+" "+code.get(Field.NAME)+" failed to resolve any versions in validity range "+code.get(Field.VALID_FROM_IN_REQUESTED_RANGE).asText()+" - "+(code.has(Field.VALID_TO_IN_REQUESTED_RANGE) ? code.get(Field.VALID_TO_IN_REQUESTED_RANGE).asText() : "null"));
                codesArrayNode.set(i, code);
            }
            LOG.debug("codesArrayNode size "+codesArrayNode.size());
            editableVersion.set(Field.CODES, codesArrayNode);
        }
        return editableVersion;
    }

    public static JsonNode addCodeVersions(JsonNode code, Logger LOG) throws HttpClientErrorException {
        ObjectNode editableCode = code.deepCopy();
        code = null;
        String validFromInRequestedRange = editableCode.get(Field.VALID_FROM_IN_REQUESTED_RANGE).asText();
        String validUntilInRequestedRange = editableCode.has(Field.VALID_TO_IN_REQUESTED_RANGE) ? editableCode.get(Field.VALID_TO_IN_REQUESTED_RANGE).asText() : null;
        String classificationID = editableCode.get(Field.CLASSIFICATION_ID).asText();
        ResponseEntity<JsonNode> classificationJsonNodeRE = KlassURNResolver.getFrom(KlassURNResolver.makeKLASSClassificationURL(classificationID));
        if (!classificationJsonNodeRE.getStatusCode().is2xxSuccessful())
            throw new HttpClientErrorException(classificationJsonNodeRE.getStatusCode(), "Did not successfully retrieve classification "+classificationID+" from klass api");
        ArrayNode klassClassificationVersions = (ArrayNode)classificationJsonNodeRE.getBody().get(Field.VERSIONS);
        ArrayNode classificationVersionLinksArrayNode = new ObjectMapper().createArrayNode();
        for (JsonNode classificationVersion : klassClassificationVersions) {
            String classificationVersionValidFrom = classificationVersion.get(Field.VALID_FROM).asText();
            String classificationVersionValidUntil = classificationVersion.has("validTo") ? classificationVersion.get("validTo").asText() : null;
            LOG.debug("Classification version '"+classificationVersion.get(Field.NAME).asText()+" has validFrom "+classificationVersionValidFrom+" and validTo "+(classificationVersionValidUntil != null ? classificationVersionValidUntil : "null"));
            if (validUntilInRequestedRange == null || classificationVersionValidFrom.compareTo(validUntilInRequestedRange) <= 0) {
                LOG.debug("Classification version '"+classificationVersion.get(Field.NAME).asText()+" had validFrom before the validUntilInRequestedRange of the code");
                if (classificationVersionValidUntil == null || classificationVersionValidUntil.compareTo(validFromInRequestedRange) >= 0) {
                    LOG.debug("Classification version '"+classificationVersion.get(Field.NAME).asText()+" had a classificationVersionValidUntil == null || classificationVersionValidUntil.compareTo(validFromInRequestedRange) >= 0 ");
                    String codeVersionURL = classificationVersion.get(Field._LINKS).get(Field.SELF).get("href").asText();
                    classificationVersionLinksArrayNode.add(codeVersionURL);
                }
            }
        }
        editableCode.set(Field.CLASSIFICATION_VERSIONS, classificationVersionLinksArrayNode);
        return editableCode;
    }

    public static String getNowDate() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat(ISO_DATE_PATTERN);
        df.setTimeZone(tz);
        return df.format(new Date());
    }

    public static int dateStringToInt(String dateString) {
        String dateStringClean = dateString.replaceAll("-","");
        return Integer.parseInt(dateStringClean);
    }

    public static ResponseEntity<JsonNode> checkAgainstSchema(JsonNode definition, JsonNode instance, Logger LOG) {
        JsonNode definitionPoperties = definition.get("properties");
        Iterator<String> submittedFieldNamesIterator = instance.fieldNames();
        while (submittedFieldNamesIterator.hasNext()) {
            String field = submittedFieldNamesIterator.next();
            LOG.debug("Checking the field '"+field+"' from the instance against the given definition . . .");
            if (!definitionPoperties.has(field)) {
                LOG.error("the definition had no such property as '"+field+"', which makes it illegal");
                return ErrorHandler.newHttpError(
                        "Submitted field "+field+" is not legal in this definition",
                        BAD_REQUEST,
                        LOG);
            }
            else if (definitionPoperties.get(field).has("type") && definitionPoperties.get(field).get("type").asText().equals("array") && !instance.get(field).isArray())
                return ErrorHandler.newHttpError("Submitted field "+field+" has to be an array according to the definition, but was not an array", BAD_REQUEST, LOG);
        }

        if (definitionPoperties.has("codes")) {
            LOG.debug("'codes' field present in definition. Checking each element of instance against ClassificationSubsetCode definition");
            JsonNode codesProperty = definitionPoperties.get("codes");
            if (codesProperty.has("type") && codesProperty.get("type").asText().equals("array")){
                LOG.debug("'codes' property in definition has a type that is array");
                if (codesProperty.has("items") && codesProperty.get("items").has("$ref") && codesProperty.get("items").get("$ref").asText().split("/")[2].equals("ClassificationSubsetCode")) {
                    LOG.debug("'codes' property in definition has items of type ClassificationSubsetCode");
                    if (instance.has("codes")) {
                        LOG.debug("the instance has a field called 'codes'");
                        JsonNode codes = instance.get("codes");
                        if (codes.isArray() && !codes.isEmpty()) {
                            LOG.debug("The field 'codes' of the instance is a non-empty array of size " + codes.size());
                            ArrayNode codesArray = codes.deepCopy();
                            ResponseEntity<JsonNode> codeDefRE = new LDSFacade().getSubsetCodeDefinition();
                            if (!codeDefRE.getStatusCode().is2xxSuccessful())
                                return codeDefRE;
                            JsonNode codeDefinition = codeDefRE.getBody();
                            for (JsonNode code : codesArray) {
                                ResponseEntity<JsonNode> validateCodeRE = Utils.checkAgainstSchema(codeDefinition, code, LOG);
                                if (!validateCodeRE.getStatusCode().is2xxSuccessful())
                                    return validateCodeRE;
                            }
                        } else {
                            LOG.warn("'codes' field in instance was not array or was empty");
                        }
                    } else {
                        LOG.warn("'codes' field was not present in the instance that is being validated");
                    }
                } else {
                    LOG.warn("definition.codes.items.$ref was not present or was not ClassificationSubsetSeries");
                }
            } else {
                LOG.warn("'codes' field was present in definition, but it was not and array AND of type ClassificationSubsetCode according to the check");
            }
        }
        LOG.debug("All the fields in the instance were present in the definition");
        ArrayNode required = (ArrayNode) definition.get("required");
        LOG.debug("There are "+required.size()+" required fields in the definition. Checking that they are all present");
        for (JsonNode jsonNode : required) {
            String requiredField = jsonNode.asText();
            LOG.debug("Checking that the required field "+requiredField+" is present in the instance");
            if (!instance.has(requiredField)){
                LOG.error("The instance did not have the required field '"+requiredField+"'");
                return ErrorHandler.newHttpError("Submitted version did not contain required field "+requiredField,
                        BAD_REQUEST,
                        LOG);
            }
        }
        LOG.debug("The instance had all required fields, and all the present fields were defined in the schema.");
        return new ResponseEntity<>(OK);
    }

    public static ObjectNode addLinksToSubsetVersion(JsonNode versionJsonNode) {
        ObjectNode editableVersion = versionJsonNode.deepCopy();
        editableVersion.set(Field._LINKS, Utils.getSubsetVersionLinkNode(editableVersion));
        return editableVersion;
    }
}
