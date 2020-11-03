package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    public static boolean isYearMonthDay(String date){
        return date.matches(YEAR_MONTH_DAY_REGEX);
    }

    public static boolean isVersion(String version){
        return version.matches(VERSION_STRING_REGEX);
    }

    public static boolean isClean(String str){
        return str.matches(CLEAN_ID_REGEX);
    }

    public static JsonNode getSubsetVersionLinkNode(JsonNode subset){
        String selfURN = getVersionLink(subset.get(Field.SERIES_ID).asText(), subset.get(Field.VERSION).asText());
        String seriesUrn = getSeriesLink(subset.get(Field.SERIES_ID).asText());
        ObjectNode linksObject = getLinkSelfObject(selfURN);
        linksObject.set("series", getHrefNode(seriesUrn));
        return linksObject;
    }

    public static ObjectNode getHrefNode(String urn){
        ObjectNode seriesNode = new ObjectMapper().createObjectNode();
        seriesNode.put("href", urn);
        return seriesNode;
    }

    public static ObjectNode getLinkSelfObject(String resourceUrn){
        ObjectNode linksNode = new ObjectMapper().createObjectNode();
        linksNode.set("self", getHrefNode(resourceUrn));
        return linksNode;
    }

    public static String getVersionLink(String seriesUID, String versionID){
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

    public static JsonNode cleanSubsetVersion(JsonNode subset){
        if (subset.isArray()){
            ArrayNode arrayNode = (ArrayNode) subset;
            return cleanSubsetVersionsArray(arrayNode);
        }
        ObjectNode clone = subset.deepCopy();
        if (!clone.has(Field.VERSION)) {
            LoggerFactory.getLogger(Utils.class).error("subset did not contain 'version', so it could not be cleaned");
            return clone;
        }
        String oldVersion = clone.get(Field.VERSION).asText();
        String majorVersion = oldVersion.split("\\.")[0];
        clone.put(Field.VERSION, majorVersion);
        return clone;
    }

    private static ArrayNode cleanSubsetVersionsArray(ArrayNode subsetArray){
        ArrayNode clone = subsetArray.deepCopy();
        for (int i = 0; i < subsetArray.size(); i++) {
            clone.set(i, cleanSubsetVersion(clone.get(i)));
        }
        return clone;
    }

    public static JsonNode getLatestMajorVersion(ArrayNode majorVersionsArrayNode, boolean published){
        JsonNode latestVersionNode = null;
        String latestVersionValidFrom = "0";
        for (JsonNode versionNode : majorVersionsArrayNode) {
            String thisVersionValidFrom = versionNode.get(Field.VERSION_VALID_FROM).asText();
            boolean isOpen = versionNode.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN);
            int compareThisToLatest = thisVersionValidFrom.compareTo(latestVersionValidFrom);
            if (compareThisToLatest == 0){
                Logger logger = LoggerFactory.getLogger(Utils.class);
                logger.error("Two major versions of a subset have the same 'versionValidFrom' values. The versions are '"+versionNode.get(Field.VERSION)+"' and '"+latestVersionNode.get(Field.VERSION)+"'");
            }
            if ((!published || isOpen) && compareThisToLatest > 0 ){
                latestVersionNode = versionNode;
                latestVersionValidFrom = thisVersionValidFrom;
            }
        }
        return latestVersionNode;
    }

    /**
     * Sort an ArrayNode of versions according to their versionValidFrom fields
     * @param subsetArrayNode
     * @return
     */
    public static ArrayNode sortByVersionValidFrom(ArrayNode subsetArrayNode){
        List<JsonNode> subsetList = new ArrayList<>(subsetArrayNode.size());
        subsetArrayNode.forEach(subsetList::add);
        subsetList.sort(Utils::versionComparator);
        ArrayNode newArrayNode = new ObjectMapper().createArrayNode();
        subsetList.forEach(newArrayNode::add);
        return newArrayNode;
    }

    public static boolean isNumeric(String string){
        if (string == null)
            return false;

        try {
            Double.parseDouble(string);
            return true;
        } catch (NumberFormatException e){
            return false;
        }
    }

    public static boolean isInteger(String string){
        if (isNumeric(string)){
            double d = Double.parseDouble(string);
            return (d % 1) == 0 && !Double.isInfinite(d);
        }
        return false;
    }

    public static int versionComparator(JsonNode s1, JsonNode s2){
        return s2.get(Field.VERSION_VALID_FROM).asText().compareTo(s1.get(Field.VERSION_VALID_FROM).asText());
    }

    public static String generateURN(String classification, String code) {
        return String.format(URN_FORMAT, classification, code);
    }

    public static String generateURN(String classification, String code, String name) {
        String encodedName = null;
        try {
            encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return String.format(URN_FORMAT_ENCODED_NAME, classification, code, encodedName);
    }

    public static String generateURN(String classification, String code, String name, String validFrom) {
        String encodedName = null;
        try {
            encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return String.format(URN_FORMAT_VALID_FROM_ENCODED_NAME, classification, code, validFrom, encodedName);
    }

    public static String generateURN(JsonNode code, String versionValidFrom){
        String name = code.get(Field.NAME).asText();
        String classification = code.get(Field.CLASSIFICATION_ID).asText();
        String encodedName = null;
        try {
            encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return String.format(URN_FORMAT_VALID_FROM_ENCODED_NAME, classification, code, versionValidFrom, encodedName);
    }

    public static ObjectNode addCodeVersionAndValidFromToAllCodesInVersion(JsonNode subsetVersion, Logger LOG){
        ObjectNode editableVersion = subsetVersion.deepCopy();
        LOG.debug("Finding out what classification versions the codes in the subsetVersion are used in");
        if (editableVersion.has(Field.CODES)){
            ArrayNode codesArrayNode = (ArrayNode)editableVersion.get(Field.CODES);
            for (int i = 0; i < codesArrayNode.size(); i++) {
                LOG.debug("Resolving code "+i+"/"+codesArrayNode.size());
                JsonNode code = null;
                code = Utils.addCodeVersionAndValidFrom(codesArrayNode.get(i));
                codesArrayNode.set(i, code);
            }
            editableVersion.set(Field.CODES, codesArrayNode);
        }
        return editableVersion;
    }

    public static JsonNode addCodeVersionAndValidFrom(JsonNode code) throws HttpClientErrorException {
        ObjectNode editableCode = code.deepCopy();
        code = null;
        String validFromInRequestedRange = editableCode.get(Field.VALID_FROM_IN_REQUESTED_RANGE).asText();
        String validUntilInRequestedRange = editableCode.has(Field.VALID_UNTIL_IN_REQUESTED_RANGE) ? editableCode.get(Field.VALID_UNTIL_IN_REQUESTED_RANGE).asText() : null;
        String classificationID = editableCode.get(Field.CLASSIFICATION_ID).asText();
        ResponseEntity<JsonNode> classificationJsonNodeRE = KlassURNResolver.getFrom(KlassURNResolver.makeKLASSClassificationURL(classificationID));
        if (!classificationJsonNodeRE.getStatusCode().is2xxSuccessful())
            throw new HttpClientErrorException(classificationJsonNodeRE.getStatusCode(), "Did not successfully retrieve classification "+classificationID+" from klass api");
        ArrayNode klassClassificationVersions = (ArrayNode)classificationJsonNodeRE.getBody().get(Field.VERSIONS);
        ArrayNode classificationVersionLinksArrayNode = new ObjectMapper().createArrayNode();
        for (JsonNode classificationVersion : klassClassificationVersions) {
            String classificationVersionValidFrom = classificationVersion.get(Field.VALID_FROM).asText();
            if (classificationVersionValidFrom.compareTo(validFromInRequestedRange) >= 0){
                if (validUntilInRequestedRange == null || classificationVersionValidFrom.compareTo(validUntilInRequestedRange) <= 0) {
                    String codeVersionURL = classificationVersion.get(Field._LINKS).get(Field.SELF).get("href").asText();
                    classificationVersionLinksArrayNode.add(codeVersionURL);
                }
            }
        }
        editableCode.set(Field.VERSIONS, classificationVersionLinksArrayNode);
        return editableCode;
        //throw new HttpClientErrorException(BAD_REQUEST, "Could not find a version of the classification "+classificationID+" that encompassed the validFromInRequestedRange "+validFromInRequestedRange);
    }

    public static String getNowDate() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat(ISO_DATE_PATTERN);
        df.setTimeZone(tz);
        return df.format(new Date());
    }

    public static ResponseEntity<JsonNode> checkAgainstSchema(JsonNode definition, JsonNode instance, Logger LOG){
        JsonNode properties = definition.get("properties");
        Iterator<String> submittedFieldNamesIterator = instance.fieldNames();
        while (submittedFieldNamesIterator.hasNext()){
            String field = submittedFieldNamesIterator.next();
            if (!properties.has(field)){
                return ErrorHandler.newHttpError(
                        "Submitted field "+field+" is not legal in ClassificationSubsetVersion",
                        BAD_REQUEST,
                        LOG);
            }
        }

        ArrayNode required = (ArrayNode) definition.get("required");
        for (JsonNode jsonNode : required) {
            String requiredField = jsonNode.asText();
            if (!instance.has(requiredField)){
                return ErrorHandler.newHttpError("Submitted version did not contain required field "+requiredField,
                        BAD_REQUEST,
                        LOG);
            }
        }
        return new ResponseEntity<>(OK);
    }

    public static ObjectNode addLinksToSubsetVersion(JsonNode versionJsonNode) {
        ObjectNode editableVersion = versionJsonNode.deepCopy();
        editableVersion.set(Field._LINKS, Utils.getSubsetVersionLinkNode(editableVersion));
        return editableVersion;
    }
}
