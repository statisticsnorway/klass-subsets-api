package no.ssb.subsetsservice.entity;

public class SQL {

    public static String SELECT_SERIES_BY_ID = "SELECT series.series_json FROM series WHERE series.series_id = ?;";
    public static String SELECT_ALL_SERIES = "SELECT series.series_json FROM series;";
    public static String UPDATE_SERIES = "UPDATE series SET series_json = ? WHERE series_id = ?;";
    public static String ADD_VERSION_TO_SERIES = "UPDATE series SET series_json = jsonb_set(series_json, '{versions,99999}'::text[], to_jsonb(?::text), true) WHERE series_id = ?;";

    public static String SELECT_VERSION_BY_ID = "SELECT versions.version_json FROM versions WHERE versions.version_id = ?;";
    public static String SELECT_VERSIONS_BY_SERIES = "SELECT versions.version_json FROM versions WHERE versions.series_id = ?;";
    public static String UPDATE_VERSION = "UPDATE versions SET version_json = ? WHERE series_id = ? AND version_id = ?;";

    public static String DELETE_SERIES = "DELETE FROM series;";
    public static String DELETE_SERIES_BY_ID = "DELETE FROM series WHERE series.series_id = ?;";
    public static String DELETE_VERSIONS_IN_SERIES = "DELETE FROM versions WHERE versions.series_id = ?;";
    public static String DELETE_VERSIONS = "DELETE FROM versions;";
    public static String DELETE_VERSIONS_BY_ID = "DELETE FROM versions WHERE versions.series_id = ? AND versions.version_id = ?;";


}
