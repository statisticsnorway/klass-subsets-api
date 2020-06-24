package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class Utils {

    public static boolean isYearMonthDay(String date){
        return date.matches("([12]\\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01]))");
    }

    public static boolean isVersion(String version){
        return version.matches("(\\d(\\.\\d)?(\\.\\d)?)");
    }

    public static boolean isClean(String str){
        return str.matches("^[a-zA-Z0-9-_]+$");
    }

    public static JsonNode getSelfLinkObject(ObjectMapper mapper, ServletUriComponentsBuilder servletUriComponentsBuilder, JsonNode subset){
        String subsetVersion = subset.get("version").textValue().split("\\.")[0];
        ObjectNode hrefNode = mapper.createObjectNode();
        hrefNode.put("href", servletUriComponentsBuilder.toUriString()+"/"+subsetVersion);
        ObjectNode self = mapper.createObjectNode();
        self.set("self", hrefNode);
        return self;
    }

    public static String getNowISO(){
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        return df.format(new Date());
    }

    public static JsonNode cleanSubsetVersion(JsonNode subset){
        if (subset.isArray()){
            ArrayNode arrayNode = (ArrayNode) subset;
            return cleanSubsetVersionsArray(arrayNode);
        }
        ObjectNode clone = subset.deepCopy();
        String oldVersion = clone.get("version").asText();
        String majorVersion = oldVersion.split("\\.")[0];
        clone.put("version", majorVersion);
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
        int latestVersion = -1;
        for (JsonNode versionNode : majorVersionsArrayNode) {
            int thisVersion = Integer.parseInt(versionNode.get("version").asText().split("\\.")[0]);
            if ((!published || versionNode.get("administrativeStatus").asText().equals("OPEN")) && thisVersion > latestVersion ){
                latestVersionNode = versionNode;
                latestVersion = thisVersion;
            }
        }
        return latestVersionNode;
    }

    public static void sortByVersion(ArrayNode subsetArrayNode){
        List<JsonNode> subsetList = new ArrayList<>(subsetArrayNode.size());
        subsetArrayNode.forEach(subsetList::add);
        subsetList.sort((s1, s2) -> Integer.compare(Integer.parseInt(s2.get("version").asText().split("\\.")[0]), Integer.parseInt(s1.get("version").asText().split("\\.")[0])));
        for (int i = 0; i < subsetList.size(); i++) {
            subsetArrayNode.set(i, subsetList.get(i));
        }
    }

}
