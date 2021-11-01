-- noinspection SqlNoDataSourceInspectionForFile

-- subsets Access
create USER subsets with PASSWORD 'subsets';
create DATABASE subsets;
grant all privileges on DATABASE subsets TO subsets;
alter role subsets SUPERUSER;

-- series
create TABLE IF NOT EXISTS public.series (
    "series_id" character varying(128) NOT NULL,
	"series_json" jsonb NOT NULL,
	CONSTRAINT series_pkey PRIMARY KEY ("series_id")
);
alter table public.series OWNER to subsets;
-- versions
create TABLE IF NOT EXISTS public.versions (
    "version_id" character varying(128) COLLATE pg_catalog."default" NOT NULL,
    "series_id" character varying(128) COLLATE pg_catalog."default" NOT NULL,
    "version_json" jsonb NOT NULL,
	CONSTRAINT versions_pkey PRIMARY KEY ("version_id"),
	CONSTRAINT "seriesIdFk" FOREIGN KEY ("series_id")
	REFERENCES public.series ("series_id") MATCH SIMPLE
	ON update NO ACTION
	ON delete NO ACTION
);

alter table public.versions OWNER to subsets;
create INDEX IF NOT EXISTS "seriesIndex" ON public.versions USING btree
("series_id" varchar_pattern_ops ASC NULLS LAST);

