package org.psjobergprivat.elasticlab.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class ElasticsearchGateway {

    @Inject
    ElasticsearchClient client;

    public SearchHits search(String indexName, Query query, int size) throws IOException {
        SearchResponse<Map> response = client.search(request -> request
                        .index(indexName)
                        .size(size)
                        .query(query),
                Map.class);

        long total = response.hits().total() != null ? response.hits().total().value() : 0L;
        List<SearchHit> hits = response.hits().hits().stream()
                .map(this::toSearchHit)
                .toList();
        return new SearchHits(total, hits);
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

    @SuppressWarnings("unchecked")
    private SearchHit toSearchHit(Hit<Map> hit) {
        Map<String, Object> source = (Map<String, Object>) hit.source();
        return new SearchHit(hit.id(), hit.score(), source);
    }
}
