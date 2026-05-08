package org.psjobergprivat.elasticlab.data;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import org.psjobergprivat.elasticlab.elasticsearch.ElasticsearchGateway;
import org.psjobergprivat.elasticlab.elasticsearch.IndexResult;
import org.psjobergprivat.elasticlab.elasticsearch.SearchHits;
import org.psjobergprivat.elasticlab.testdata.GenerationParameters;
import org.psjobergprivat.elasticlab.testdata.GenerationStatus;
import org.psjobergprivat.elasticlab.testdata.TestDataService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
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

    @Inject
    TestDataService testData;

    @ConfigProperty(name = "elastic-lab.default-elastic-index")
    String defaultElasticIndex;

    @GET
    public SearchHits listDocuments(
            @QueryParam("from") @DefaultValue("0") int from,
            @QueryParam("size") @DefaultValue("50") int size) throws IOException {
        int cappedSize = Math.min(size, MAX_LISTED_DOCUMENTS);
        return elasticsearch.search(defaultElasticIndex, MatchAllQuery.of(m -> m)._toQuery(), cappedSize, from);
    }

    @POST
    public IndexResult createDocument(Map<String, Object> document) throws IOException {
        testData.ensureIndexExists();
        return elasticsearch.indexDocument(defaultElasticIndex, document);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteDocument(@PathParam("id") String id) throws IOException {
        elasticsearch.deleteDocument(defaultElasticIndex, id);
        return Response.noContent().build();
    }

    @POST
    @Path("/preview")
    public Map<String, Object> previewGeneratedDocument(GenerationParameters params) {
        validate(params);
        return testData.preview(params);
    }

    @POST
    @Path("/generate")
    public GenerationStatus startGeneration(GenerationParameters params) {
        validate(params);
        try {
            return testData.startGeneration(params);
        } catch (IllegalStateException alreadyRunning) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", alreadyRunning.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }
    }

    @GET
    @Path("/generate/status")
    public GenerationStatus generationStatus() {
        return testData.getStatus();
    }

    @DELETE
    public Map<String, Object> deleteAll() throws IOException {
        long deleted = testData.deleteAll();
        return Map.of("deleted", deleted);
    }

    private void validate(GenerationParameters params) {
        if (params == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing parameters"))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }
    }
}
