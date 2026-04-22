package org.psjobergprivat.elasticlab.data;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import org.psjobergprivat.elasticlab.elasticsearch.ElasticsearchGateway;
import org.psjobergprivat.elasticlab.elasticsearch.IndexResult;
import org.psjobergprivat.elasticlab.elasticsearch.SearchHits;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.Map;

@Path("/data")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DataResource {

    private static final int MAX_LISTED_DOCUMENTS = 100;

    @Inject
    ElasticsearchGateway elasticsearch;

    @ConfigProperty(name = "elastic-lab.default-index")
    String defaultIndex;

    @GET
    public SearchHits listDocuments() throws IOException {
        return elasticsearch.search(defaultIndex, MatchAllQuery.of(m -> m)._toQuery(), MAX_LISTED_DOCUMENTS);
    }

    @POST
    public IndexResult createDocument(Map<String, Object> document) throws IOException {
        return elasticsearch.indexDocument(defaultIndex, document);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteDocument(@PathParam("id") String id) throws IOException {
        elasticsearch.deleteDocument(defaultIndex, id);
        return Response.noContent().build();
    }
}
