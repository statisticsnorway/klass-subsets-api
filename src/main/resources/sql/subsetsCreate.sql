CREATE TABLE IF NOT EXISTS public.series
(
    "series_id" character varying(128) NOT NULL,
    "series_json" jsonb NOT NULL,
    CONSTRAINT series_pkey PRIMARY KEY ("series_id")
);

ALTER TABLE public.series
    OWNER to klass_subsets;

CREATE TABLE IF NOT EXISTS public.versions
(
    "version_id" character varying(128) COLLATE pg_catalog."default" NOT NULL,
    "series_id" character varying(128) COLLATE pg_catalog."default" NOT NULL,
    "version_json" jsonb NOT NULL,
    CONSTRAINT versions_pkey PRIMARY KEY ("version_id"),
    CONSTRAINT "seriesIdFk" FOREIGN KEY ("series_id")
        REFERENCES public.series ("series_id") MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

ALTER TABLE public.versions
    OWNER to klass_subsets;

CREATE INDEX IF NOT EXISTS "seriesIndex"
    ON public.versions USING btree
    ("series_id" varchar_pattern_ops ASC NULLS LAST)
;
