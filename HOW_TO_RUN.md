# Elastic Lab — how to run

## Prerequisites

- Java 21
- Maven 3.9+
- Podman (or Docker) with compose support

## 1. Start Elasticsearch

From the repo root:

```bash
podman compose up -d          # or: docker compose up -d
```

Elasticsearch listens on <http://localhost:9200>.

Stop with `podman compose down` (add `-v` to also drop the data volume).

## 2. Start the backend (serves the frontend too)

From the repo root:

```bash
cd backend
mvn quarkus:dev
```

The backend listens on <http://localhost:8080>:

- UI: <http://localhost:8080/>
- API: `/api/search`, `/api/data`

The frontend lives in `../frontend` and is wired into the backend build via
`pom.xml` (a `<resources>` entry targeting `META-INF/resources`), so a single
`mvn quarkus:dev` launches the full app. Edits under `frontend/` are picked up
by Quarkus dev mode — just refresh the browser.

## 3. Package for deployment

```bash
cd backend
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## Layout

| Path                 | Purpose                                  |
|----------------------|------------------------------------------|
| `docker-compose.yml` | Elasticsearch container                  |
| `backend/`           | Quarkus + REST API                       |
| `frontend/`          | Ext JS SPA (served by Quarkus)           |
| `requirements/`      | Functional & technical requirements      |
