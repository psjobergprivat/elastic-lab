package org.psjobergprivat.elasticlab.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.psjobergprivat.elasticlab.elasticsearch.ElasticsearchGateway;
import org.psjobergprivat.elasticlab.elasticsearch.SearchHits;
import org.psjobergprivat.elasticlab.metadata.IndexMetadata;
import org.psjobergprivat.elasticlab.metadata.IndexMetadataService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.Map;

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SearchResource {

    private static final int MAX_HITS = 100;

    @Inject
    ElasticsearchGateway elasticsearch;

    @Inject
    IndexMetadataService indexMetadataService;

    @Inject
    QueryCompiler queryCompiler;

    @ConfigProperty(name = "elastic-lab.default-elastic-index")
    String defaultIndex;

    @POST
    public SearchHits search(SearchRequest request) throws IOException {
        String indexName = (request != null && request.indexName() != null && !request.indexName().isBlank())
                ? request.indexName()
                : defaultIndex;
        Map<String, Object> mappings = loadMappings(indexName);
        QueryNode root = request != null ? request.root() : null;
        Query query = queryCompiler.compile(root, mappings);
        int size = (request != null && request.size() != null) ? Math.min(request.size(), MAX_HITS) : MAX_HITS;
        int from = (request != null && request.from() != null) ? request.from() : 0;
        return elasticsearch.search(indexName, query, size, from);
    }

    private Map<String, Object> loadMappings(String indexName) throws IOException {
        IndexMetadata metadata = indexMetadataService.loadMetadata(indexName);
        return metadata.mappings() != null ? metadata.mappings() : Map.of();
    }

    public record SearchRequest(String indexName, QueryNode root, Integer from, Integer size) {
    }
}
