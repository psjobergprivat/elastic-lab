package org.psjobergprivat.elasticlab.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.JsonpMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.stream.JsonGenerator;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class ElasticsearchGateway {

    @Inject
    ElasticsearchClient client;

    // Java's type erasure prevents Map<String, Object>.class, so Map (raw) is the standard
    // pattern for dynamic documents with the ES Java client.
    @SuppressWarnings({"rawtypes", "unchecked"})
    public SearchHits search(String indexName, Query query, int size, int from) throws IOException {
        SearchResponse<Map> response = client.search(request -> request
                        .index(indexName)
                        .size(size)
                        .from(from)
                        .query(query),
                Map.class);

        long total = response.hits().total() != null ? response.hits().total().value() : 0L;
        List<SearchHit> hits = response.hits().hits().stream()
                .map(hit -> new SearchHit(hit.id(), hit.score(), (Map<String, Object>) hit.source()))
                .toList();
        return new SearchHits(total, hits, serializeQuery(query));
    }

    private String serializeQuery(Query query) {
        if (query == null) {
            return null;
        }
        JsonpMapper mapper = client._jsonpMapper();
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
            query.serialize(generator, mapper);
        }
        return writer.toString();
    }

    public IndexResult indexDocument(String indexName, Map<String, Object> document) throws IOException {
        IndexResponse response = client.index(request -> request
                .index(indexName)
                .document(document)
                .refresh(Refresh.WaitFor));
        return new IndexResult(response.id());
    }

    public void deleteDocument(String indexName, String id) throws IOException {
        DeleteResponse response = client.delete(request -> request
                .index(indexName)
                .id(id)
                .refresh(Refresh.WaitFor));
        Objects.requireNonNull(response);
    }

    public void bulkIndex(String indexName, List<Map<String, Object>> documents) throws IOException {
        if (documents.isEmpty()) {
            return;
        }
        BulkRequest.Builder builder = new BulkRequest.Builder().index(indexName);
        for (Map<String, Object> document : documents) {
            builder.operations(op -> op.index(idx -> idx.document(document)));
        }
        BulkResponse response = client.bulk(builder.build());
        if (response.errors()) {
            String firstError = response.items().stream()
                    .map(BulkResponseItem::error)
                    .filter(Objects::nonNull)
                    .map(e -> e.type() + ": " + e.reason())
                    .findFirst()
                    .orElse("unknown");
            throw new IOException("Bulk index reported errors: " + firstError);
        }
    }

    public void refresh(String indexName) throws IOException {
        if (!indexExists(indexName)) {
            return;
        }
        client.indices().refresh(r -> r.index(indexName));
    }

    public boolean indexExists(String indexName) throws IOException {
        return client.indices().exists(e -> e.index(indexName)).value();
    }

    public void createIndex(String indexName, String createBodyJson) throws IOException {
        try (Reader reader = new StringReader(createBodyJson)) {
            client.indices().create(c -> c.index(indexName).withJson(reader));
        }
    }

    public void dropIndex(String indexName) throws IOException {
        if (!indexExists(indexName)) {
            return;
        }
        client.indices().delete(d -> d.index(indexName));
    }

    public long countDocuments(String indexName) throws IOException {
        if (!indexExists(indexName)) {
            return 0L;
        }
        CountResponse response = client.count(c -> c.index(indexName));
        return response.count();
    }

}
