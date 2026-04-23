package org.psjobergprivat.elasticlab.testdata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@ApplicationScoped
public class DocumentGenerator {

    private static final List<String> CONTAINER_NAMES = List.of(
            "details", "metadata", "info", "extra", "context", "section", "scope",
            "nested_items", "nested_events", "nested_records");

    private static final List<String> FLATTENED_KEYS = List.of(
            "color", "size", "region", "source", "channel");

    private static final String JOIN_TYPE = "join";

    private static final long DATE_RANGE_DAYS = 365L;

    @Inject
    FieldCatalog catalog;

    @Inject
    ValueSourceRegistry valueSources;

    public Map<String, Object> generate(GenerationParameters params, Random random) {
        int requestedFields = randomBetween(params.minFields(), params.maxFields(), random);
        int depth = randomBetween(params.minDepth(), params.maxDepth(), random);
        List<FieldDefinition> selected = pickFields(requestedFields, random);
        List<List<FieldDefinition>> byLevel = distributeAcrossLevels(selected, depth, random);
        return buildDocument(byLevel, depth, random);
    }

    private int randomBetween(int min, int max, Random random) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }

    private List<FieldDefinition> pickFields(int count, Random random) {
        List<FieldDefinition> all = catalog.all();
        if (all.isEmpty()) {
            throw new IllegalStateException("Field catalog is empty");
        }
        List<FieldDefinition> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled, random);
        if (count <= shuffled.size()) {
            return shuffled.subList(0, count);
        }
        List<FieldDefinition> result = new ArrayList<>(count);
        result.addAll(shuffled);
        while (result.size() < count) {
            result.add(shuffled.get(random.nextInt(shuffled.size())));
        }
        return result;
    }

    private List<List<FieldDefinition>> distributeAcrossLevels(List<FieldDefinition> fields, int depth, Random random) {
        List<List<FieldDefinition>> byLevel = new ArrayList<>(depth);
        for (int i = 0; i < depth; i++) {
            byLevel.add(new ArrayList<>());
        }
        List<FieldDefinition> joinFields = new ArrayList<>();
        List<FieldDefinition> regular = new ArrayList<>();
        for (FieldDefinition fd : fields) {
            (JOIN_TYPE.equals(fd.type()) ? joinFields : regular).add(fd);
        }
        byLevel.get(0).addAll(joinFields);
        Collections.shuffle(regular, random);
        int seedCount = Math.min(depth, regular.size());
        for (int i = 0; i < seedCount; i++) {
            byLevel.get(i).add(regular.remove(0));
        }
        for (FieldDefinition fd : regular) {
            byLevel.get(random.nextInt(depth)).add(fd);
        }
        return byLevel;
    }

    private Map<String, Object> buildDocument(List<List<FieldDefinition>> byLevel, int depth, Random random) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> current = root;
        for (int level = 0; level < depth; level++) {
            for (FieldDefinition fd : byLevel.get(level)) {
                String key = uniqueKey(current, fd.name());
                current.put(key, generateValue(fd, random));
            }
            if (level + 1 < depth) {
                String containerKey = uniqueKey(current, CONTAINER_NAMES.get(random.nextInt(CONTAINER_NAMES.size())));
                Map<String, Object> next = new LinkedHashMap<>();
                current.put(containerKey, next);
                current = next;
            }
        }
        return root;
    }

    private String uniqueKey(Map<String, Object> existing, String desired) {
        if (!existing.containsKey(desired)) {
            return desired;
        }
        int suffix = 2;
        while (existing.containsKey(desired + "_" + suffix)) {
            suffix++;
        }
        return desired + "_" + suffix;
    }

    private Object generateValue(FieldDefinition fd, Random random) {
        String source = fd.source();
        if (source.startsWith("file:")) {
            List<String> values = valueSources.get(source.substring("file:".length()));
            return values.get(random.nextInt(values.size()));
        }
        if (source.startsWith("multi:")) {
            List<String> values = valueSources.get(source.substring("multi:".length()));
            int picks = 1 + random.nextInt(Math.min(5, values.size()));
            List<String> shuffled = new ArrayList<>(values);
            Collections.shuffle(shuffled, random);
            return new ArrayList<>(shuffled.subList(0, picks));
        }
        if (source.startsWith("constant:")) {
            return source.substring("constant:".length());
        }
        if ("uuid".equals(source)) {
            return UUID.randomUUID().toString();
        }
        return generateRandomByType(fd.type(), random);
    }

    private Object generateRandomByType(String type, Random random) {
        return switch (type) {
            case "boolean" -> random.nextBoolean();
            case "long" -> 1L + (long) random.nextInt(1_000_000);
            case "double" -> Math.round(random.nextDouble() * 100_000.0) / 100.0;
            case "date" -> randomIsoDate(random);
            case "ip" -> randomIp(random);
            case "version" -> random.nextInt(10) + "." + random.nextInt(30) + "." + random.nextInt(200);
            case "binary" -> randomBase64(random, 16);
            case "long_range" -> randomLongRange(random);
            case "double_range" -> randomDoubleRange(random);
            case "date_range" -> randomDateRange(random);
            case "ip_range" -> randomIpRange(random);
            case "flattened" -> randomFlattened(random);
            case "join" -> randomJoinValue(random);
            case "keyword", "wildcard", "constant_keyword" -> randomWord(random);
            default -> randomWord(random);
        };
    }

    private String randomWord(Random random) {
        List<String> words = valueSources.get("english_words.csv");
        return words.get(random.nextInt(words.size()));
    }

    private String randomIsoDate(Random random) {
        long offsetDays = random.nextInt((int) DATE_RANGE_DAYS);
        Instant when = Instant.now().minus(offsetDays, ChronoUnit.DAYS).minusSeconds(random.nextInt(86_400));
        return DateTimeFormatter.ISO_INSTANT.format(when);
    }

    private String randomIp(Random random) {
        return random.nextInt(223) + 1
                + "." + random.nextInt(256)
                + "." + random.nextInt(256)
                + "." + (random.nextInt(254) + 1);
    }

    private String randomBase64(Random random, int byteCount) {
        byte[] bytes = new byte[byteCount];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private Map<String, Object> randomLongRange(Random random) {
        long lower = random.nextInt(500);
        long upper = lower + 1 + random.nextInt(500);
        return Map.of("gte", lower, "lte", upper);
    }

    private Map<String, Object> randomDoubleRange(Random random) {
        double lower = Math.round(random.nextDouble() * 100.0 * 100.0) / 100.0;
        double upper = lower + Math.round(random.nextDouble() * 100.0 * 100.0) / 100.0 + 0.01;
        return Map.of("gte", lower, "lte", upper);
    }

    private Map<String, Object> randomDateRange(Random random) {
        long offsetDays = random.nextInt((int) DATE_RANGE_DAYS);
        Instant start = Instant.now().minus(offsetDays, ChronoUnit.DAYS);
        Instant end = start.plus(1L + random.nextInt(30), ChronoUnit.DAYS);
        return Map.of(
                "gte", DateTimeFormatter.ISO_INSTANT.format(start),
                "lte", DateTimeFormatter.ISO_INSTANT.format(end));
    }

    private Map<String, Object> randomIpRange(Random random) {
        int firstOctet = random.nextInt(223) + 1;
        int secondOctet = random.nextInt(256);
        String prefix = firstOctet + "." + secondOctet + ".";
        int low = random.nextInt(128);
        int high = low + 1 + random.nextInt(127);
        return Map.of(
                "gte", prefix + "0." + low,
                "lte", prefix + "255." + high);
    }

    private Map<String, Object> randomFlattened(Random random) {
        List<String> keys = new ArrayList<>(FLATTENED_KEYS);
        Collections.shuffle(keys, random);
        int entries = 2 + random.nextInt(Math.min(3, keys.size() - 2 + 1));
        List<String> words = valueSources.get("english_words.csv");
        Map<String, Object> flat = new LinkedHashMap<>();
        for (int i = 0; i < entries; i++) {
            flat.put(keys.get(i), words.get(random.nextInt(words.size())));
        }
        return flat;
    }

    private Map<String, Object> randomJoinValue(Random random) {
        Map<String, Object> join = new LinkedHashMap<>();
        join.put("name", "parent");
        return join;
    }
}
