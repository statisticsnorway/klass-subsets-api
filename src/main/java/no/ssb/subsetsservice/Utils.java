package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class Utils {

    public static final String ISO_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"; // Quoted "Z" to indicate UTC, no timezone offset
    public static final String CLEAN_ID_REGEX = "^[a-zA-Z0-9-_]+$";
    public static final String YEAR_MONTH_DAY_REGEX = "([12]\\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01]))";
    public static final String VERSION_STRING_REGEX = "(\\d(\\.\\d)?(\\.\\d)?)";

    public static boolean isYearMonthDay(String date){
        return date.matches(YEAR_MONTH_DAY_REGEX);
    }

    public static boolean isVersion(String version){
        return version.matches(VERSION_STRING_REGEX);
    }

    public static boolean isClean(String str){
        return str.matches(CLEAN_ID_REGEX);
    }

    public static JsonNode getSelfLinkObject(ObjectMapper mapper, ServletUriComponentsBuilder servletUriComponentsBuilder, JsonNode subset){
        ObjectNode hrefNode = mapper.createObjectNode();
        String urlBase = servletUriComponentsBuilder.toUriString().split("subsets")[0];
        String resourceUrn = urlBase+"subsets/"+subset.get("id")+"/versions/"+subset.get(Field.VERSION);
        hrefNode.put("href", resourceUrn);
        ObjectNode self = mapper.createObjectNode();
        self.set("self", hrefNode);
        return self;
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
            if ((!published || isOpen) && thisVersionValidFrom.compareTo(latestVersionValidFrom) > 0 ){
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
        subsetList.sort((s1, s2) -> s1.get(Field.VERSION_VALID_FROM).asText().compareTo(s2.get(Field.VERSION_VALID_FROM).asText()));
        ArrayNode newArrayNode = new ObjectMapper().createArrayNode();
        subsetList.forEach(newArrayNode::add);
        return newArrayNode;
    }

}
