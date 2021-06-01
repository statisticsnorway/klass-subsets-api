package no.ssb.subsetsservice;

public class BackendFactory {

    public static final String MONGO = "MONGO";
    public static final String LDS = "LDS";
    public static final String DEFAULT_BACKEND = LDS;

    public static BackendInterface getBackend(String backendType) {
        if (backendType.equalsIgnoreCase(LDS))
            return new LDSFacade();
        else if (backendType.toUpperCase().startsWith(MONGO))
            return new MongoFacade();
        else
            return null;
    }
}
