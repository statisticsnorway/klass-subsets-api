# klass-subsets-api
KLASS Subsets REST Service, implemented in Spring Boot.

This API serves to store, search and maintain KLASS subsets ("uttrekk").

As a database we use an instance of Linked Data Store (LDS documentation: https://github.com/statisticsnorway/linked-data-store-documentation/tree/master/docs Docker Hub: https://hub.docker.com/r/statisticsnorway/lds-server/tags) with a Postgres backend.

This service replaced a Node.js prototype, which can be found at: https://github.com/statisticsnorway/klass-subsets-service

# API documentation

This API mimics the [KLASS Classifications API](https://data.ssb.no/api/klass/v1/api-guide.html) in many ways.

## V2 API
Swagger UI OpenAPI definition: [https://subsets-api.staging-bip-app.ssb.no/swagger-ui/index.html?configUrl=/api-docs/swagger-config](https://subsets-api.staging-bip-app.ssb.no/swagger-ui/index.html?configUrl=/api-docs/swagger-config)

This version of the API treats the information relation to the Classification Subset Series separately from the information related to the Classification Subset Versions that belong to the series.

- `POST /v2/subsets` without any subset versions inside it to create a new subset series. See example of valid subset series POST/PUT requests in the "subset data structure" section below.
- `PUT /v2/subsets/{seriesID}` to edit the series. PUT requests to the series can not edit or add versions.
- `POST /v2/subsets/{seriesID}/versions` to add a Version to a Series. See example of valid versions in the "subset data structure" section below.
- `PUT /v2/subsets/{seriesID}/versions/{version}`to edit a version. `{version}` is a unique identifier generated at POST time.
- `GET /v2/subsets/{seriesID}/codes` to retrieve a list of the codes that are valid today. 
    - Optional query parameters "from" and "to" take dates on form "YYYY-MM-DD". When both are given, a list containing all codes that are valid in all versions from the "from" date to the "to" date will be returned. Example: `GET /v2/subsets/{seriesID}/codes?from=2019-11-02&to=2020-03-20`
- `GET /v2/subsets/{seriesID}/codesAt?date=YYYY-MM-DD` to retrieve a list of the codes valid on the given date
- `GET /v2/subsets/{seriesID}/versions` to retrieve a list of all versions of this subset
- `GET /v2/subsets/{seriesID}/versions/{version}` to retrieve the version with the UID `version`, or the UID `seriesID_version` if it exists
- Where it makes sense there are optional `includeDrafts` and `includeFuture` boolean parameters. `includeDrafts` includes versions of subsets that are not currently published. `includeFuture` includes versions of subsets that will only be valid from a future date.

### Deletion
At the moment we allow for complete deletion of subset series and versions, for development purposes. This will be restricted later on.
- `DELETE /v2/subset/{seriesID}` will delete the series and all versions related to it
- `DELETE /v2/subset/{seriesID}/versions/{versionID}` will delete the version

### Subset data structure
A description to the subset data structure is available here `GET /v2/subsets/schema`.

This is an example of the contents of a valid POST to `/v2/subsets`:
```
{
	"id":"UID_for_dette_uttrekket_1",
	"description": [
        	{"languageCode":"nb", "languageText":"tekst på norsk bokmål"}, 
        	{"languageCode":"en", "languageText":"text in english"}
    	],
	"name": [
        	{"languageCode":"nb", "languageText":"fullt navn på norsk bokmål"}, 
        	{"languageCode":"en", "languageText":"full name in english"}
    	],
	"administrativeDetails": [
        	{"administrativeDetailType":"DEFAULTLANGUAGE", "values":["nb"]}
    	],
	"owningSection":"700 it",
	"classificationFamily":"Some Family"
}
```

The field `id` needs to contain a unique identifier for the series. Legal characters are a-z, A-Z, 0-9, `_` and `-`.
At least one description and one name must be given, in the default language.

Once a subset series has been successfully created, you can add versions to it. This is an example of a valid POST to `/v2/subsets/UID_for_dette_uttrekket_1/versions` *after* the POST request of the series above has been made:

```
{
	"validFrom":"2020-10-19",
	"validUntil":"2021-10-19",
	"administrativeStatus":"DRAFT",
	"codes":[
	{
      		"classificationId": "131",
      		"code": "1144",
      		"rank": "1",
      		"level": "1",
      		"name": "Kvitsøy",
      		"validFromInRequestedRange": "2020-10-19",
      		"validToInRequestedRange": "2021-10-19"
	}
	],
	"versionRationale": [
        	{"languageCode":"nb", "languageText":"versjon rasjonale på norsk bokmål"}, 
        	{"languageCode":"en", "languageText":"version rationale in english"}
    	]
}

```

If you want the server to delete any fields with names that do not fit in the schema instead of returning a Bad Request code, you can use the request parameter `ignoreSuperfluousFields`.

When the administrative status is `DRAFT`, anything in the version is subject to change, and it is possible to have an empty `codes` array. If the status is `OPEN` on the other hand, the only change possible is this: If `validUntil` is not set to anyting, you are allowed to set it.

The `validFromInRequestedRange` of a code indicates the earliest point in the subset version's validity period (starting at `validFrom`) where this code with this name and level is also valid. If the code with this name and level is valid from some point before the subset version's `validFrom` date, then the `validFromInRequestedRange` of the code will be equal to the `validFrom` of the subset version.

The `validToInRequestedRange` of a code indicates the latest point in the subset version's validity period (ending with `validUntil`) where this code with this name and level is also valid. If the code with this name and level is valid from some point after the subset version's `validUntil` date, then the `validToInRequestedRange` of the code will be equal to the `validUntil` of the subset version.

If we GET `v2/subsets/UID_for_dette_uttrekket_1` the response will look like this:
```
{
  "classificationFamily": "Some Family",
  "classificationType": "Subset",
  "createdDate": "2020-11-10",
  "id": "UID_for_dette_uttrekket_1",
  "lastModified": "2020-11-10T15:23:42Z",
  "owningSection": "700 it",
  "statisticalUnits": [
    "Region"
  ],
  "versions": [
    "/v2/subsets/UID_for_dette_uttrekket_1/versions/UID_for_dette_uttrekket_1_e815e953-90dc-4103-b592-0f3b231dfeb3"
  ],
  "administrativeDetails": [
    {
      "administrativeDetailType": "DEFAULTLANGUAGE",
      "values": [
        "nb"
      ]
    }
  ],
  "description": [
    {
      "languageCode": "nb",
      "languageText": "tekst på norsk bokmål"
    },
    {
      "languageCode": "en",
      "languageText": "text in english"
    }
  ],
  "name": [
    {
      "languageCode": "nb",
      "languageText": "fullt navn på norsk bokmål"
    },
    {
      "languageCode": "en",
      "languageText": "full name in english"
    }
  ],
  "_links": {
    "self": {
      "href": "/v2/subsets/UID_for_dette_uttrekket_1"
    }
  }
}
```

`versions` will be an array of links to subset versions that are members of this series. Date of creation and last time of modification have been automatically stamped by the server.

If we GET the subset version that we just created (`/v2/subsets/UID_for_dette_uttrekket_1/versions/UID_for_dette_uttrekket_1_e815e953-90dc-4103-b592-0f3b231dfeb3`) the response will look like this:

```
{
  "administrativeStatus": "DRAFT",
  "createdDate": "2020-12-22",
  "lastModified": "2020-12-22T12:12:27Z",
  "subsetId": "UID_for_dette_uttrekket_1",
  "validFrom": "2020-10-19",
  "validUntil": "2021-10-19",
  "versionId": "6b23ba81-b90c-4b6a-92ca-06d16ab7bd53",
  "statisticalUnits": [
    "Region"
  ],
  "codes": [
    {
      "classificationId": "131",
      "code": "1144",
      "level": "1",
      "rank": "1",
      "validFromInRequestedRange": "2020-10-19",
      "validToInRequestedRange": "2021-10-19",
      "classificationVersions": [
        "https://data.ssb.no/api/klass/v1/versions/1160"
      ],
      "name": [
        {
          "languageCode": "nb",
          "languageText": "Kvitsøy"
        },
        {
          "languageCode": "nn",
          "languageText": "Kvitsøy"
        },
        {
          "languageCode": "en",
          "languageText": "Kvitsøy"
        }
      ]
    }
  ],
  "versionRationale": [
    {
      "languageCode": "nb",
      "languageText": "versjon rasjonale på norsk bokmål"
    },
    {
      "languageCode": "en",
      "languageText": "version rationale in english"
    }
  ],
  "_links": {
    "self": {
      "href": "/v2/subsets/UID_for_dette_uttrekket_1/versions/6b23ba81-b90c-4b6a-92ca-06d16ab7bd53"
    },
    "series": {
      "href": "/v2/subsets/UID_for_dette_uttrekket_1"
    }
  }
}
```

#### Version UID
The version has gotten an automatically generated v4 UUID version id, stored in the field `versionId`. Combining the `subsetId` and the `versionId` number will give us a version UID. If we want to GET the version entity.

#### Classification versions of codes
Observe that at this point the single code included in our version has a new field called `classificationVersions`. This field was generated when the version was POSTed. The `classificationVersions` array in each code contains a link to each classification version that was valid and had this exact name and level in the subset version's validity period at the time of creation. When a subset version has a validity period extending into the future, it might be that a new version of a classification comes out that also contains that code, or does not contain that code, or that makes edits to that code. Storing an array of versions that existed and contained the code in the validity period at the time of creation ensures our ability to detect and resolve possible conflicts and changes to the code, and to avoid including unintended versions of a code.

#### Names of codes
Notice that the "name" field of the code has been transformed from a single string to an array of MultilingualText objects. When a code is posted to a version, its names in all languages are retrieved from KLASS API and stored with the code. When using a GET request that returns codes at any level, you can use the request parameter `language` with the value `nb` (norsk bokmål) or `nn` (norsk nynorsk) or `en` (english), to receive the codes with only a string value of the selected language in the "name" field. The default value of the parameter is `all`, which returns the codes with the array of MultilingualText objects.

The schema for the Series as stored in LDS: https://lds-klass.staging-bip-app.ssb.no/ns/ClassificationSubsetSeries?schema
The schema for the Versions as stored in LDS: https://lds-klass.staging-bip-app.ssb.no/ns/ClassificationSubsetVersion?schema

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
