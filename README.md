# klass-subsets-api
KLASS Subsets REST Service, implemented in Spring Boot.

This API will serve to store, search and maintain KLASS subsets ("uttrekk").

**The application is under development.**

The goal is to replace the existing Node + Express implementation, which can be found at: https://github.com/statisticsnorway/klass-subsets-service

# API documentation

The core functionality of the API:
- GET and POST requests to "/v1/subsets". (GET gets all subsets, POST posts a single subset. The ID of the subset is found inside the JSON)
- GET and PUT requests to "/v1/subsets/{id}" to retrieve or change a subset with a specific id.

In addition, we support getting the subset schema at "/v1/subsets?schema"
And routing GET requests for codes to Klass codes API at "/v1/codes" or "/v1/codes/{id}"


# Testing against LDS locally

This application does not store any data locally. It uses LDS as its database. In order for it to work, it must be connected to an instance of LDS.

To set up local instance of LDS against which you can test API calls on your development machine, follow the steps in the README of this repository: https://github.com/statisticsnorway/klass-subsets-setup
