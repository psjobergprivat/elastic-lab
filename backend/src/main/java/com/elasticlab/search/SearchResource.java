package com.elasticlab.search;

import com.elasticlab.elasticsearch.ElasticsearchGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    @Inject
    ElasticsearchGateway elasticsearch;

    @ConfigProperty(name = "elastic-lab.default-index")
    String defaultIndex;

    @POST
    public JsonNode search(JsonNode requestBody) throws IOException {
        JsonNode effectiveQuery = (requestBody == null || requestBody.isMissingNode() || requestBody.isNull() || requestBody.isEmpty())
                ? matchAll()
                : requestBody;
        return elasticsearch.search(defaultIndex, effectiveQuery);
    }

    private ObjectNode matchAll() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.putObject("query").putObject("match_all");
        return root;
    }
}
