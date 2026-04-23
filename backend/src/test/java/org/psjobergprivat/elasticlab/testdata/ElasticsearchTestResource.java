package org.psjobergprivat.elasticlab.testdata;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class ElasticsearchTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.3.3";
    private static final String TEST_INDEX_NAME = "elastic-lab-test";

    private ElasticsearchContainer container;

    @Override
    public Map<String, String> start() {
        DockerImageName image = DockerImageName.parse(IMAGE)
                .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch");
        container = new ElasticsearchContainer(image)
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
        container.start();
        return Map.of(
                "quarkus.elasticsearch.hosts", container.getHttpHostAddress(),
                "elastic-lab.default-elastic-index", TEST_INDEX_NAME);
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
