# Search Flow Overview

## Request / Response Flow

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant SR as SearchResource
    participant IMS as IndexMetadataService
    participant QC as QueryCompiler
    participant ESG as ElasticsearchGateway
    participant ES as Elasticsearch

    FE->>SR: POST /search (SearchRequest)
    SR->>IMS: loadMetadata(indexName)
    IMS->>ESG: getMapping(indexName)
    ESG->>ES: GET /{index}/_mapping
    ES-->>ESG: mapping JSON
    ESG-->>IMS: IndexMetadata
    IMS-->>SR: mappings: Map<String,Object>
    SR->>QC: compile(root, mappings)
    QC-->>SR: ES Query
    SR->>ESG: search(indexName, query, maxHits=100)
    ESG->>ES: POST /{index}/_search
    ES-->>ESG: SearchResponse
    ESG-->>SR: SearchHits(total, hits, esQuery)
    SR-->>FE: SearchHits (JSON)
```

## Component Relations

```mermaid
classDiagram
    direction TB

    class SearchResource {
        +search(SearchRequest) SearchHits
        -loadMappings(indexName) Map
    }
    class SearchRequest {
        String indexName
        QueryNode root
    }
    class QueryCompiler {
        +compile(QueryNode, mappings) Query
    }
    class PhoneNumberNormalizer {
        +normalize(raw, region) Optional~Normalized~
    }
    class IndexMetadataService {
        +loadMetadata(indexName) IndexMetadata
    }
    class ElasticsearchGateway {
        +search(index, query, size) SearchHits
    }
    class SearchHits {
        long total
        List~SearchHit~ hits
        String esQuery
    }
    class SearchHit {
        String id
        Double score
        Map source
    }

    SearchResource --> QueryCompiler : compile query
    SearchResource --> IndexMetadataService : load mappings
    SearchResource --> ElasticsearchGateway : execute search
    SearchResource ..> SearchRequest : receives
    SearchResource ..> SearchHits : returns
    SearchHits "1" *-- "0..*" SearchHit
    SearchRequest --> QueryNode
    QueryCompiler --> PhoneNumberNormalizer : valueType="phone"
```

## QueryNode Tree

```mermaid
classDiagram
    direction TB

    class QueryNode {
        <<interface>>
    }
    class GroupNode {
        Operator operator
        List~QueryNode~ children
    }
    class NotNode {
        QueryNode child
    }
    class PropertyNode {
        String path
        String valueType
        String value
    }
    class TypeAllNode {
        String valueType
        String value
    }
    class GlobalNode {
        String value
    }
    class FreeTextNode {
        String value
    }
    class Operator {
        <<enum>>
        AND
        OR
    }

    QueryNode <|.. GroupNode
    QueryNode <|.. NotNode
    QueryNode <|.. PropertyNode
    QueryNode <|.. TypeAllNode
    QueryNode <|.. GlobalNode
    QueryNode <|.. FreeTextNode

    GroupNode --> Operator
    GroupNode --> "0..*" QueryNode : children
    NotNode --> "0..1" QueryNode : child
```

## Phone-aware Property Compile

A `PropertyNode` with `valueType="phone"` takes a different path than the
typed branches. The `path` field is ignored; the query always targets the three
top-level catchall fields populated at index time by `PhoneCatchallCollector`
and `PhoneNumberNormalizer`:

| Field | Indexed value |
| --- | --- |
| `phone_all_canonical` | E.164 digits (CC + national significant number) for every phone in the doc. |
| `phone_all_subscriber` | National significant number alone (no CC, no trunk) for every phone. |
| `phone_all_subscriber_national` | Same as `_subscriber`, but only contributed by phones that were written in **national** form. |

`QueryCompiler.compilePhone` picks the matching shape based on whether the
input parses as international (`+` / `00` prefix) or national:

```mermaid
flowchart TD
    A[PropertyNode<br/>valueType=phone] --> B[PhoneNumberNormalizer.normalize]
    B -->|libphonenumber| C{International<br/>input?}
    B -.->|fallback heuristic<br/>strip leading 0| C
    C -->|yes| D[bool should]
    D --> E[term phone_all_canonical = fullDigits]
    D --> F[term phone_all_subscriber_national = subscriber]
    C -->|no| G[term phone_all_subscriber = subscriber]
```

The asymmetry of `phone_all_subscriber_national` — only national-format docs
contribute — is what keeps two international-format docs in different countries
from matching each other when they happen to share a subscriber. The
international compile branch never queries `phone_all_subscriber`, so an
intl-X query cannot reach an intl-Y doc.

National-form queries depend on libphonenumber knowing the trunk-prefix rules
of the caller's country (Russia's `8`, Hungary's `06`, etc.). The default
region for query parsing is read from
`elastic-lab.phone.default-search-region`; when unset, queries fall back to a
digit-only heuristic that strips a single leading `0`.
