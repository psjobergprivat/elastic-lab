package com.elasticlab.data;

import com.elasticlab.elasticsearch.ElasticsearchGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;

@Path("/data")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DataResource {

    private static final int DEFAULT_LIST_SIZE = 100;

    @Inject
    ElasticsearchGateway elasticsearch;

    @ConfigProperty(name = "elastic-lab.default-index")
    String defaultIndex;

    @GET
    public JsonNode listDocuments() throws IOException {
        return elasticsearch.search(defaultIndex, listAllQuery());
    }

    @POST
    public JsonNode createDocument(JsonNode document) throws IOException {
        return elasticsearch.indexDocument(defaultIndex, document);
    }

    @DELETE
    @Path("/{id}")
    public JsonNode deleteDocument(@PathParam("id") String id) throws IOException {
        return elasticsearch.deleteDocument(defaultIndex, id);
    }

    private ObjectNode listAllQuery() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("size", DEFAULT_LIST_SIZE);
        root.putObject("query").putObject("match_all");
        return root;
    }
}
