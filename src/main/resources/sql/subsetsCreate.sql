CREATE TABLE IF NOT EXISTS public.series
(
    "seriesId" character varying(128) NOT NULL,
    "seriesJSON" jsonb NOT NULL,
    CONSTRAINT series_pkey PRIMARY KEY ("seriesId")
);

ALTER TABLE public.series
    OWNER to postgres;

CREATE TABLE IF NOT EXISTS public.versions
(
    "versionId" character varying(128) COLLATE pg_catalog."default" NOT NULL,
    "seriesId" character varying(128) COLLATE pg_catalog."default" NOT NULL,
    "versionJSON" jsonb NOT NULL,
    CONSTRAINT versions_pkey PRIMARY KEY ("versionId"),
    CONSTRAINT "seriesIdFk" FOREIGN KEY ("seriesId")
        REFERENCES public.series ("seriesId") MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

ALTER TABLE public.versions
    OWNER to postgres;

CREATE INDEX IF NOT EXISTS "seriesIndex"
    ON public.versions USING btree
    ("seriesId" varchar_pattern_ops ASC NULLS LAST)
;
