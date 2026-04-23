package org.psjobergprivat.elasticlab.testdata;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ValueSourceRegistry {

    private static final String VALUE_SOURCE_DIRECTORY = "testdata/values/";

    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public List<String> get(String fileName) {
        return cache.computeIfAbsent(fileName, this::load);
    }

    private List<String> load(String fileName) {
        String resourcePath = VALUE_SOURCE_DIRECTORY + fileName;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Value source not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> values = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        values.add(trimmed);
                    }
                }
                if (values.isEmpty()) {
                    throw new IllegalStateException("Value source is empty: " + resourcePath);
                }
                return List.copyOf(values);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load value source " + resourcePath, e);
        }
    }
}
