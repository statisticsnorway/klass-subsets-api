package no.ssb.subsetsservice.service;

public class BackendFactory {

    public static final String POSTGRES = "POSTGRES";
    public static final String DEFAULT_BACKEND = POSTGRES;

    public static BackendInterface getBackend(String backendType) {
        switch (backendType.toUpperCase()) {
            case POSTGRES: return new PostgresFacade();
            default: return null;
        }
    }
}
