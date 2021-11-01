-- noinspection SqlNoDataSourceInspectionForFile

-- subsets Access
create USER subsets with PASSWORD 'postgres';
create DATABASE subsets;
grant all privileges on DATABASE subsets TO subsets;
alter role subsets SUPERUSER;
commit;

