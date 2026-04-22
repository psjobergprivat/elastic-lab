package org.psjobergprivat.elasticlab.search;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import org.psjobergprivat.elasticlab.elasticsearch.ElasticsearchGateway;
import org.psjobergprivat.elasticlab.elasticsearch.SearchHits;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SearchResource {

    private static final int MAX_HITS = 100;

    @Inject
    ElasticsearchGateway elasticsearch;

    @ConfigProperty(name = "elastic-lab.default-elastic-index")
    String defaultIndex;

    @POST
    public SearchHits search(SearchRequest request) throws IOException {
        Query query = buildQuery(request);
        return elasticsearch.search(defaultIndex, query, MAX_HITS);
    }

    private Query buildQuery(SearchRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return MatchAllQuery.of(m -> m)._toQuery();
        }
        return QueryStringQuery.of(q -> q.query(request.text()))._toQuery();
    }

    public record SearchRequest(String text) {
    }
}
