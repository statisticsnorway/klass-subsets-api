package no.ssb.subsetsservice.service;

public class DatabaseFactory {

    public static final String POSTGRES = "POSTGRES";
    public static final String DEFAULT_DATABASE = POSTGRES;

    public static DatabaseInterface getDatabase(String databaseType) {
        switch (databaseType.toUpperCase()) {
            case POSTGRES: return new PostgresFacade();
            default: return null;
        }
    }
}
