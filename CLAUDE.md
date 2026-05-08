# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All Maven commands run from `backend/`.

- **Run dev**: `mvn quarkus:dev` — starts backend on `http://localhost:8080` and serves the frontend at `/`. Quarkus dev mode picks up edits under `frontend/` live; just refresh the browser.
- **Package**: `mvn package` then `java -jar target/quarkus-app/quarkus-run.jar`.
- **All tests**: `mvn test` (Testcontainers spins up a throwaway ES 9.3.3 container).
- **Single test**: `mvn test -Dtest=TestDataFlowTest` or `-Dtest=TestDataFlowTest#previewProducesDocumentWithFields`.

Tests need a working Docker-API socket. With Podman (the project's default):

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

Elasticsearch for `mvn quarkus:dev` runs separately via `podman compose up -d` (or `docker compose up -d`) at the repo root, listening on `localhost:9200`. Tests do **not** use this — they run against a Testcontainer and target a separate `elastic-lab-test` index.

## Architecture

### Single-deployable layout

The frontend is an Ext JS 6.2.0 SPA that lives in `frontend/META-INF/resources/`. It is **not** built separately — `backend/pom.xml` adds `${project.basedir}/../frontend` as a `<resources>` entry, so its files end up on the classpath and Quarkus serves them as static resources. There is one deployable: the Quarkus jar.

REST endpoints are mounted under `/api` (configured via `quarkus.rest.path=/api`). The frontend always calls relative URLs like `/api/search`, so the SPA and API are same-origin.

### Search pipeline

Search is the most non-trivial flow. The frontend builds a `QueryNode` tree (a sealed interface with `GroupNode`, `NotNode`, `PropertyNode`, `TypeAllNode`, `GlobalNode`, `FreeTextNode`) representing the user's UI-built query. `SearchResource` receives this tree, loads the index mapping via `IndexMetadataService`, and hands both to `QueryCompiler`, which walks the tree and emits a typed `co.elastic.clients...Query` using the official Elasticsearch Java client.

Key contract in `QueryCompiler`: each `compileX` returns `Optional<Query>`. Empty means "no constraint" — blank values, empty groups, NOT-with-no-child are all dropped. If the entire tree compiles to empty, the result falls back to `match_all`. Validity rules per node are documented in the `QueryCompiler` Javadoc; keep them there if extending.

`TypeAllNode` ("match all fields of type X") is resolved by `QueryCompiler.collectFieldsByType`, which walks the live mapping (including nested `properties` and `fields` sub-fields) — this is why the compiler needs the mapping passed in.

`ElasticsearchGateway.search` serializes the executed `Query` back to JSON via the client's `JsonpMapper` and returns it on `SearchHits.esQuery`. The frontend displays it in the "Elastic Query Viewer" panel — that round-trip is a feature, not debugging output, so don't remove it.

There's a Mermaid diagram in `architecture/search-flow-overview.md` that mirrors this; update it if the search flow changes shape.

### Test-data generation

`DataResource` is async: `POST /api/data/generate` returns immediately with `state=running`; the frontend polls `GET /api/data/generate/status`. Concurrency model is a single-thread `ExecutorService` in `TestDataService` plus an `AtomicReference<GenerationStatus>` — only one generation job runs at a time (second request returns 409). Don't introduce a second worker without rethinking that contract.

The generated index has an explicit mapping built by `MappingBuilder` from `src/main/resources/testdata/field_catalog.csv`. Each catalog row becomes a `dynamic_templates` entry keyed on the exact field name, so when `DocumentGenerator` emits e.g. `client_ip`, ES applies the `ip` type. A `nested_*` template forces those object containers to type `nested`. Fields not in the catalog still get ES default dynamic mapping.

`TestDataService.deleteAll()` **drops and recreates the index** — it does not delete-by-query. This is the only way to keep the explicit mapping after a wipe.

`DocumentGenerator` picks fields randomly from the catalog, distributes them across `1..maxDepth` JSON object levels, generates values either from CSVs in `testdata/values/` (`file:`, `multi:`), constants (`constant:`), UUIDs, or type-driven random (`random`). Add new field types by extending the `field_catalog.csv` row plus, if the type is unusual, a branch in `DocumentGenerator.generateRandomByType` and `MappingBuilder.matchMappingTypeFor`.

### Error mapping

`ElasticsearchExceptionMapper` and `ElasticsearchIOExceptionMapper` translate ES client errors into JSON responses with `error`/`type`/`reason`/`rootCauses`. Resource methods declare `throws IOException` and let these mappers handle the response — don't catch and re-wrap inside the resource.

### Configuration

- `elastic-lab.default-elastic-index` — index name used when `SearchRequest.indexName` is blank, and the only index `DataResource` reads/writes. The test resource overrides it to `elastic-lab-test`.
- `quarkus.elasticsearch.hosts` — ES host. Test resource overrides this to the Testcontainer address.

## Conventions (from `requirements/Technical Requirements.md`)

- Java package and Maven groupId: `org.psjobergprivat.elasticlab`.
- Quarkus 3.34.x, Elasticsearch 9.3.x, Java 21.
- Use the typed Elasticsearch Java client, not low-level REST.
- Frontend talks only to the backend API; complex logic stays on the backend.
- Descriptive names over comments — the existing code follows this consistently.

`requirements/Functional Requirements.md` is the source of truth for what the UI must do (Search / Manage Data / View Metadata tabs) — consult it before adding UI features.
