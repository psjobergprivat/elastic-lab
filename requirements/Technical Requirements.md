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
* Elasticsearch uses the "dynamic mapping" feature
* When calling Elasticsearch the latest recommended Java client supporting typed queries should be used
* Podman is used when developing locally, but the files have to be Docker compatible
* Complex logic and calculations should preferably be done in backend rather than frontend

## The frontend
* An Ext JS single page application
* Only calls the backend API

## The API - used by the frontend and published by the backend
* Uses REST json, no web sockets
