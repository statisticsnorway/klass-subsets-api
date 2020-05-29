# klass-subsets-api
KLASS Subsets REST Service, implemented in Spring Boot.

This API will serve to store, search and maintain KLASS subsets ("uttrekk").

**The application is under development.**

The goal is to replace the existing Node + Express implementation, which can be found at: https://github.com/statisticsnorway/klass-subsets-service

# API documentation

This API mimics the KLASS Classifications API as closely as is reasonable.

### Core functionality
- GET and POST `/v1/subsets`. (GET gets all subsets, POST posts a single subset. The ID of the subset is found inside the JSON)
- GET and PUT `/v1/subsets/{id}` to retrieve or change a subset with a specific id.
- `GET /v1/subsets/{id}/codes` to retrieve a list of the valid codes in the latest version of this subset. 
    - Optional query parameters "from" and "to" take dates on form "YYYY-MM-DD". When both are given, a list containing all codes that are valid in all versions from the "from" date to the "to" date will be returned.
- `GET /v1/subsets/{id}/codesAt?date=YYYY-MM-DD` to retrieve a list of the codes valid on the given date
- `GET /v1/versions/{id}/` to retrieve a list of all versions of this subset, in descending order (most recent first).
- `GET /v1/versions/{id}/{version}` to retrieve a list of versions that start with {version}

### Misc
In addition, we support getting the subset schema at "/v1/subsets?schema"
And routing GET requests for codes to Klass codes API at "/v1/codes" or "/v1/codes/{id}"


# Testing against LDS locally

This application does not store any data locally. It uses LDS as its database. In order for it to work, it must be connected to an instance of LDS.

To set up local instance of LDS against which you can test API calls on your development machine, follow the steps in the README of this repository: https://github.com/statisticsnorway/klass-subsets-setup
