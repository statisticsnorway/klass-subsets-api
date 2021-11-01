package no.ssb.subsetsservice.controller;

public class Scripts {

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

}
