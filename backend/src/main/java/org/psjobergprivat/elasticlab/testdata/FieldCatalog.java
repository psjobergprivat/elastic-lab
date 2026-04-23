package org.psjobergprivat.elasticlab.testdata;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FieldCatalog {

    private static final String CATALOG_RESOURCE = "testdata/field_catalog.csv";

    private List<FieldDefinition> fields = List.of();

    @PostConstruct
    void load() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(CATALOG_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Field catalog resource not found: " + CATALOG_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<FieldDefinition> loaded = new ArrayList<>();
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    if (header) {
                        header = false;
                        continue;
                    }
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    String[] parts = trimmed.split(",", 3);
                    if (parts.length < 3) {
                        throw new IllegalStateException("Invalid catalog row: " + line);
                    }
                    loaded.add(new FieldDefinition(parts[0].trim(), parts[1].trim(), parts[2].trim()));
                }
                fields = List.copyOf(loaded);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load field catalog", e);
        }
    }

    public List<FieldDefinition> all() {
        return fields;
    }
}
