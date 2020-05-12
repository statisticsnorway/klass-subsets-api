# klass-subsets-api
Subsets REST Service

This API will serve to store, search and maintain KLASS subsets ("uttrekk").

**The application is under development.**

The goal is to replace the existing Node + Express implementation, which can be found at: https://github.com/statisticsnorway/klass-subsets-service


# Testing against LDS locally

This aplication does not store any data locally. It uses LDS as its database.

To test API calls against a local instance of LDS, follow these steps:

- Get the existing RAML schemas from here: https://github.com/statisticsnorway/gsim-raml-schema
- Convert the RAML schemas to a GraphQL file called `schemas.graphql` using this: https://github.com/statisticsnorway/raml-to-graphql-schema
- Get the docker image for LDS server: `docker pull statisticsnorway/lds-server`
- Run a LDS instance with in memory data: `docker run -it -p 9090:9090 -v **<full path to folder containing "schemas.graphql">**:/lds/schemas statisticsnorway/lds-server:latest`
- LDS is now reachable on http://localhost:9090
