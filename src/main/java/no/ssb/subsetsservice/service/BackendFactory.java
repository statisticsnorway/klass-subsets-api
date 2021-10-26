package no.ssb.subsetsservice.service;

public class BackendFactory {

    public static final String MONGO = "MONGO";
    public static final String POSTGRES = "POSTGRES";
    public static final String LDS = "LDS";
    public static final String DEFAULT_BACKEND = POSTGRES;

    public static BackendInterface getBackend(String backendType) {
        switch (backendType.toUpperCase()) {
            case LDS: return new LDSFacade();
            case MONGO: return new MongoFacade();
            case POSTGRES: return new PostgresFacade();
            default: return null;
        }
    }
}
