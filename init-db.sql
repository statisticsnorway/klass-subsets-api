-- noinspection SqlNoDataSourceInspectionForFile

-- Dataset Access
CREATE USER klass_subsets WITH PASSWORD 'postgres';
CREATE DATABASE klass_subsets;
GRANT ALL PRIVILEGES ON DATABASE klass_subsets TO klass_subsets;