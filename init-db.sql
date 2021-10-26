-- noinspection SqlNoDataSourceInspectionForFile

-- Dataset Access
CREATE USER postgres_klass WITH PASSWORD 'postgres';
CREATE DATABASE postgres_klass;
GRANT ALL PRIVILEGES ON DATABASE postgres_klass TO postgres_klass;