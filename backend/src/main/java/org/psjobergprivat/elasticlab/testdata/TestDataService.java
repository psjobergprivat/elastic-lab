package org.psjobergprivat.elasticlab.testdata;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.psjobergprivat.elasticlab.elasticsearch.ElasticsearchGateway;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class TestDataService {

    private static final Logger LOG = Logger.getLogger(TestDataService.class);
    private static final int BULK_BATCH_SIZE = 500;

    @Inject
    DocumentGenerator generator;

    @Inject
    MappingBuilder mappingBuilder;

    @Inject
    ElasticsearchGateway elasticsearch;

    @ConfigProperty(name = "elastic-lab.default-elastic-index")
    String defaultIndex;

    private final AtomicReference<GenerationStatus> status = new AtomicReference<>(GenerationStatus.idle());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "test-data-generator");
        thread.setDaemon(true);
        return thread;
    });

    public Map<String, Object> preview(GenerationParameters params) {
        return generator.generate(params, ThreadLocalRandom.current());
    }

    public synchronized GenerationStatus startGeneration(GenerationParameters params) {
        GenerationStatus current = status.get();
        if (current.isRunning()) {
            throw new IllegalStateException("A generation job is already running");
        }
        GenerationStatus initial = GenerationStatus.running(params.numDocuments(), 0);
        status.set(initial);
        executor.submit(() -> runGeneration(params));
        return initial;
    }

    public GenerationStatus getStatus() {
        return status.get();
    }

    public void ensureIndexExists() throws IOException {
        if (!elasticsearch.indexExists(defaultIndex)) {
            elasticsearch.createIndex(defaultIndex, mappingBuilder.build());
        }
    }

    public long deleteAll() throws IOException {
        long previousCount = elasticsearch.countDocuments(defaultIndex);
        elasticsearch.dropIndex(defaultIndex);
        elasticsearch.createIndex(defaultIndex, mappingBuilder.build());
        return previousCount;
    }

    private void runGeneration(GenerationParameters params) {
        int total = params.numDocuments();
        int done = 0;
        try {
            ensureIndexExists();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            while (done < total) {
                int batchSize = Math.min(BULK_BATCH_SIZE, total - done);
                List<Map<String, Object>> batch = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize; i++) {
                    batch.add(generator.generate(params, random));
                }
                elasticsearch.bulkIndex(defaultIndex, batch);
                done += batchSize;
                status.set(GenerationStatus.running(total, done));
            }
            elasticsearch.refresh(defaultIndex);
            status.set(GenerationStatus.completed(total));
        } catch (Exception e) {
            LOG.error("Test data generation failed", e);
            status.set(GenerationStatus.failed(total, done, e.getMessage()));
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
