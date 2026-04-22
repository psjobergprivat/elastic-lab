package com.elasticlab.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.io.IOException;

@ApplicationScoped
public class ElasticsearchGateway {

    @Inject
    RestClient restClient;

    @Inject
    ObjectMapper objectMapper;

    public JsonNode search(String indexName, JsonNode query) throws IOException {
        Request request = new Request("POST", "/" + indexName + "/_search");
        request.setJsonEntity(objectMapper.writeValueAsString(query));
        return executeAndReadJson(request);
    }

    public JsonNode indexDocument(String indexName, JsonNode document) throws IOException {
        Request request = new Request("POST", "/" + indexName + "/_doc?refresh=wait_for");
        request.setJsonEntity(objectMapper.writeValueAsString(document));
        return executeAndReadJson(request);
    }

    public JsonNode deleteDocument(String indexName, String id) throws IOException {
        Request request = new Request("DELETE", "/" + indexName + "/_doc/" + id + "?refresh=wait_for");
        return executeAndReadJson(request);
    }

    private JsonNode executeAndReadJson(Request request) throws IOException {
        Response response = restClient.performRequest(request);
        return objectMapper.readTree(response.getEntity().getContent());
    }
}
