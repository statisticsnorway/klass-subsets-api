package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;

public class ErrorHandler {

    public static final String ILLEGAL_ID = "id contains illegal characters";
    public static final String MALFORMED_VERSION = "malformed version";

    public static ResponseEntity<JsonNode> newError(String message, HttpStatus status, Logger logger){
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("status", status.value());
        body.put("error", status.toString());
        body.put("message", message);
        body.put("timestamp", Utils.getNowISO());
        logger.error(body.toPrettyString());
        return new ResponseEntity<>(body, status);
    }

    public static ResponseEntity<JsonNode> illegalID(Logger logger){
        return newError(ErrorHandler.ILLEGAL_ID, HttpStatus.BAD_REQUEST, logger);
    }

    public static ResponseEntity<JsonNode> malformedVersion(Logger logger){
        return newError(ErrorHandler.MALFORMED_VERSION, HttpStatus.BAD_REQUEST, logger);
    }
}
