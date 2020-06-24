package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ErrorHandler {

    public static final String ILLEGAL_ID = "id contains illegal characters";
    public static final String MALFORMED_VERSION = "malformed version";

    public static ResponseEntity<JsonNode> newError(String message, HttpStatus status){
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("error", message);
        body.put("timestamp", Utils.getNowISO());
        body.put("httpStatus", status.value());
        return new ResponseEntity<>(body, status);
    }

    public static ResponseEntity<JsonNode> illegalID(){
        return newError(ErrorHandler.ILLEGAL_ID, HttpStatus.BAD_REQUEST);
    }

    public static ResponseEntity<JsonNode> malformedVersion(){
        return newError(ErrorHandler.MALFORMED_VERSION, HttpStatus.BAD_REQUEST);
    }
}
