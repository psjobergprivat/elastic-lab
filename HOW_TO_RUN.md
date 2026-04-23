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

## 3. Run the integration tests

The backend ships with a Testcontainers-based test that spins up a throwaway
Elasticsearch 9.3.3 container, drives the REST endpoints with REST-Assured,
and exercises preview / generate / list / delete-all against a dedicated
`elastic-lab-test` index.

If you use Podman (this project's default), make sure the user-level Podman
socket is running and Testcontainers can find it:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true   # avoids ryuk-on-podman flakiness
```

Then:

```bash
cd backend
mvn test
```

Each run pulls/starts a fresh container, so the first run takes ~30 s; later
runs reuse the cached image and finish in ~30 s end-to-end. The tests do not
touch the index used by `mvn quarkus:dev`.

## 4. Package for deployment

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
