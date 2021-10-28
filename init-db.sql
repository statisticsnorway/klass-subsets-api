-- noinspection SqlNoDataSourceInspectionForFile

-- Dataset Access
CREATE USER subsets WITH PASSWORD 'postgres';
CREATE DATABASE subsets;
GRANT ALL PRIVILEGES ON DATABASE subsets TO subsets;
