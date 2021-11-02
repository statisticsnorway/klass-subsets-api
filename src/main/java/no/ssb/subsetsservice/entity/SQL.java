package no.ssb.subsetsservice.entity;

public class SQL {

    public static String CREATE_SERIES = "CREATE TABLE IF NOT EXISTS public.series\n" +
            "(\n" +
            "    \"series_id\" character varying(128) NOT NULL,\n" +
            "    \"series_json\" jsonb NOT NULL,\n" +
            "    CONSTRAINT series_pkey PRIMARY KEY (\"series_id\")\n" +
            ");";
    public static String SET_OWNER_SERIES = "ALTER TABLE public.series\n" +
            "    OWNER to subsets;";
    public static String CREATE_VERSIONS = "CREATE TABLE IF NOT EXISTS public.versions\n" +
            "(\n" +
            "    \"version_id\" character varying(128) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
            "    \"series_id\" character varying(128) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
            "    \"version_json\" jsonb NOT NULL,\n" +
            "    CONSTRAINT versions_pkey PRIMARY KEY (\"version_id\"),\n" +
            "    CONSTRAINT \"seriesIdFk\" FOREIGN KEY (\"series_id\")\n" +
            "        REFERENCES public.series (\"series_id\") MATCH SIMPLE\n" +
            "        ON UPDATE NO ACTION\n" +
            "        ON DELETE NO ACTION\n" +
            ");";
    public static String SET_OWNER_VERSIONS = "ALTER TABLE public.versions\n" +
            "    OWNER to subsets;";
    public static String CREATE_INDEX = "CREATE INDEX IF NOT EXISTS \"seriesIndex\"\n" +
            "    ON public.versions USING btree\n" +
            "    (\"series_id\" varchar_pattern_ops ASC NULLS LAST)\n" +
            ";";

    public static String SELECT_SERIES_BY_ID = "SELECT series.series_json FROM series WHERE series.series_id = ?;";
    public static String SELECT_ALL_SERIES = "SELECT series.series_json FROM series;";
    public static String UPDATE_SERIES = "UPDATE series SET series_json = ? WHERE series_id = ?;";
    public static String ADD_VERSION_TO_SERIES = "UPDATE series SET series_json = jsonb_set(series_json, '{versions,99999}'::text[], to_jsonb(?::text), true) WHERE series_id = ?;";

    public static String SELECT_VERSION_BY_ID = "SELECT versions.version_json FROM versions WHERE versions.version_id = ?;";
    public static String SELECT_SERIES_VERSIONS_VALID = "SELECT versions.version_json FROM versions " +
            "WHERE versions.series_id = ? " +
            "AND versions.version_json ->> '" + Field.ADMINISTRATIVE_STATUS + "' in ? " +
            "AND CAST (versions.version_json ->> '" + Field.VALID_FROM + "' AS DATE) < CURRENT_DATE;";
    public static String SELECT_SERIES_VERSIONS_VALID_AND_FUTURE = "SELECT versions.version_json FROM versions " +
            "WHERE versions.series_id = ? " +
            "AND versions.version_json ->> '" + Field.ADMINISTRATIVE_STATUS + "' in ?";
    public static String UPDATE_VERSION = "UPDATE versions SET version_json = ? WHERE series_id = ? AND version_id = ?";

    public static String DELETE_SERIES = "DELETE FROM series;";
    public static String DELETE_SERIES_BY_ID = "DELETE FROM series WHERE series.series_id = ?;";
    public static String DELETE_VERSIONS_IN_SERIES = "DELETE FROM versions WHERE versions.series_id = ?;";
    public static String DELETE_VERSIONS = "DELETE FROM versions;";
    public static String DELETE_VERSIONS_BY_ID = "DELETE FROM versions WHERE versions.series_id = ? AND versions.version_id = ?;";


}
