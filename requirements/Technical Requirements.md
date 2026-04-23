# Fundamental technical requirements

## In general

* Best practice in architecture is important
* Code is easy to read and maintain for humans
* Descriptive names are better than comments in the code
* Avoid comments that doesn't add useful information

## The backend

* A Java application running on Quarkus
* Latest stable Quarkus 3.34.x is used
* Quarkus runs directly on the developer machine, not in a container
* Latest stable Elasticsearch 9.3.x is used
* Elasticsearch is accessed for both reads and writes
* Elasticsearch is running in a container
* The test data index is created with an explicit mapping derived from the test
  data field catalog so that each catalog field is stored with its declared
  Elasticsearch type (keyword, wildcard, ip, version, binary, constant_keyword,
  flattened, nested, join, the four range types, etc.). This is implemented
  with `dynamic_templates` matching catalog field names — Elasticsearch's
  default dynamic mapping still applies to any field that is not in the
  catalog.
* When calling Elasticsearch the latest recommended Java client supporting typed queries should be used
* Podman is used when developing locally, but the files have to be Docker compatible
* Complex logic and calculations should preferably be done in backend rather than frontend
* Maven groupId and java package is org.psjobergprivat.elasticlab

## The frontend

* An Ext JS single page application
* Only calls the backend API

## The API - used by the frontend and published by the backend

* Uses REST json, no web sockets

## Test data
* Test data can be generated and inserted upon user's request, see [Functional Requirements](./Functional%20Requirements.md)
* The following field types should be able to be part of the test data 
  binary
  boolean
  keyword - such as IDs, email addresses, hostnames, status codes, zip codes, or tags
  constant_keyword
  wildcard
  long
  double
  date
  object
  flattened
  nested
  join - Defines a parent/child relationship for documents in the same index.
  long_range
  double_range
  date_range
  ip_range
  ip
  version
* For many of the field types, values can be generated on the fly by using random values, like double.
  For other types, like keyword, the values should be randomly picked from a set of files containing data that are made up but look fairly real, like emails.csv and english_words.csv
* The field names to use is also picked from a file, which also contains info on what file to pick values from or how to generate random values.
