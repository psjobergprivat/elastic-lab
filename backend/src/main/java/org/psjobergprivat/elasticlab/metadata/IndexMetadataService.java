package org.psjobergprivat.elasticlab.metadata;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.elasticsearch.indices.IndexVersioning;
import co.elastic.clients.json.JsonpMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.stream.JsonGenerator;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class IndexMetadataService {

    @Inject
    ElasticsearchClient client;

    @Inject
    ObjectMapper objectMapper;

    public ServerMetadata loadServerMetadata() throws IOException {
        InfoResponse info = client.info();
        String version = (info.version() != null) ? info.version().number() : null;
        return new ServerMetadata(version, info.clusterName());
    }

    public List<String> listIndexNames() throws IOException {
        IndicesResponse response = client.cat().indices();
        return response.indices().stream()
                .map(IndicesRecord::index)
                .filter(name -> name != null && !name.startsWith("."))
                .sorted()
                .toList();
    }

    public IndexMetadata loadMetadata(String indexName) throws IOException {
        CountResponse count = client.count(c -> c.index(indexName));
        GetIndexResponse getResponse = client.indices().get(r -> r.index(indexName));
        IndexState state = getResponse.get(indexName);
        if (state == null) {
            return new IndexMetadata(indexName, count.count(), null, Map.of());
        }
        String indexVersion = extractIndexVersion(state);
        Map<String, Object> mappings = (state.mappings() == null) ? Map.of() : jsonpObjectToMap(state.mappings());
        return new IndexMetadata(indexName, count.count(), indexVersion, mappings);
    }

    private String extractIndexVersion(IndexState state) {
        IndexSettings settings = state.settings();
        if (settings == null || settings.index() == null || settings.index().version() == null) {
            return null;
        }
        IndexVersioning version = settings.index().version();
        return version.createdString() != null ? version.createdString() : version.created();
    }

    private Map<String, Object> jsonpObjectToMap(Object typedObject) throws IOException {
        JsonpMapper mapper = client._transport().jsonpMapper();
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
            mapper.serialize(typedObject, generator);
        }
        return objectMapper.readValue(writer.toString(), new TypeReference<>() {
        });
    }
}
