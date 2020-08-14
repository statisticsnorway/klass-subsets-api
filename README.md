# klass-subsets-api
KLASS Subsets REST Service, implemented in Spring Boot.

This API serves to store, search and maintain KLASS subsets ("uttrekk").

As a database we use an instance of Lpinked Data Store (LDS documentation: https://github.com/statisticsnorway/linked-data-store-documentation/tree/master/docs Docker Hub: https://hub.docker.com/r/statisticsnorway/lds-server/tags) with a Postgres backend.

This service replaced a Node.js prototype, which can be found at: https://github.com/statisticsnorway/klass-subsets-service

# API documentation

This API mimics the [KLASS Classifications API](https://data.ssb.no/api/klass/v1/api-guide.html) as closely as is reasonable.

### Core functionality
- GET and POST `/v1/subsets`. (GET gets all subsets, POST posts a single subset. The ID of the subset is found inside the JSON)
- GET and PUT `/v1/subsets/{id}` to retrieve or change a subset with a specific id.
- `GET /v1/subsets/{id}/codes` to retrieve a list of the valid codes in the latest version of this subset. 
    - Optional query parameters "from" and "to" take dates on form "YYYY-MM-DD". When both are given, a list containing all codes that are valid in all versions from the "from" date to the "to" date will be returned. Example: `GET /v1/subsets/{id}/codes?from=2019-11-02&to=2020-03-20`
- `GET /v1/subsets/{id}/codesAt?date=YYYY-MM-DD` to retrieve a list of the codes valid on the given date
- `GET /v1/subsets/{id}/versions` to retrieve a list of all versions of this subset, in descending order (most recent first).
- `GET /v1/subsets/{id}/versions/{version}` to retrieve a list of versions that start with {version}
- Where it makes sense there are optional `includeDraft` and `includeFuture` boolean parameters. `includeDraft` includes versions of subsets that are not currently published. `includeFuture` includes versions of subsets that will only be valid from a future date.

### Health
at `GET /health/alive` you can check whether the service is running.
at `GET /health/ready` you can cehck whether the service can reach LDS and KLASS, and that itself returns 200 OK when attempting to return all of the subsets.

### Misc
In addition, we support getting the subset schema at "/v1/subsets?schema"

# Connecting to LDS and KLASS
This service needs to be able to connect to the LDS instance's API, and the KLASS Classifications API.

Two environment variables pointing to these APIs are required  
- name: "API_LDS", value: "http://{exampleurl}/ns/ClassificationSubset"
- name: "API_KLASS", value: "https://data.ssb.no/api/klass/v1/classifications"

The `{exampleurl}` part of API_LDS value given above must be replaced with the apropriate address of the LDS instance you are using (i.e. `localhost:9090`)
The `API_KLASS API` value given is correct and reachable from anywhere as of time of writing (13. August 2020) and is expected to stay that way.

# Testing against LDS locally

This application does not store any data locally. It uses LDS as its database. In order for the service to work, it must be connected to an instance of LDS.

To set up local instance of LDS against which you can test API calls on your development machine, follow the steps in the README of this repository: https://github.com/statisticsnorway/klass-subsets-setup
