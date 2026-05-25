package org.psjobergprivat.elasticlab.testdata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.psjobergprivat.elasticlab.phone.PhoneNumberNormalizer;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@ApplicationScoped
public class DocumentGenerator {

    // --- Level 3 containers (leaves) ---

    private static final ContainerDef GEO = new ContainerDef("geo",
            List.of("latitude", "longitude"),
            List.of());

    private static final ContainerDef COMPONENT = new ContainerDef("component",
            List.of("name", "quantity", "weight", "length", "serial_number", "notes"),
            List.of());

    private static final ContainerDef SUBNET = new ContainerDef("subnet",
            List.of("client_ip", "server_ip", "gateway_ip", "allowed_ips", "protocol", "port"),
            List.of());

    // --- Level 2 containers ---

    private static final ContainerDef ADDRESS = new ContainerDef("address",
            List.of("street", "city", "zip_code", "postal_code", "country_code"),
            List.of(GEO));

    private static final ContainerDef EMPLOYMENT = new ContainerDef("employment",
            List.of("title", "organization_name", "department_name", "email", "phone", "fax", "country_code", "date_of_birth"),
            List.of());

    private static final ContainerDef FINANCIALS = new ContainerDef("financials",
            List.of("amount", "price", "currency", "tax_rate", "discount", "price_range"),
            List.of());

    private static final ContainerDef SPECS = new ContainerDef("specs",
            List.of("length", "width", "height", "weight", "color", "material", "capacity", "serial_number", "model_number", "manufacturer"),
            List.of(COMPONENT));

    private static final ContainerDef INVENTORY = new ContainerDef("inventory",
            List.of("quantity", "stock_count", "min_stock", "amount", "price", "size_range", "event_period", "category", "sku"),
            List.of());

    private static final ContainerDef HARDWARE = new ContainerDef("hardware",
            List.of("cpu_count", "memory", "disk_size", "capacity", "serial_number", "model_number"),
            List.of());

    private static final ContainerDef NETWORK = new ContainerDef("network",
            List.of("gateway_ip", "allowed_ips", "blocked_ips", "protocol", "port", "hostname"),
            List.of(SUBNET));

    private static final ContainerDef PAYLOAD = new ContainerDef("payload",
            List.of("content", "description", "notes", "score", "size_range", "score_range",
                    "priority", "severity", "error_code", "error_message", "attachment", "thumbnail"),
            List.of());

    // --- Level 1 (root) containers ---

    private static final ContainerDef PERSON = new ContainerDef("person",
            List.of("username", "display_name", "email", "mobile", "work_phone", "status",
                    "tags", "labels", "language", "last_login", "date_of_birth"),
            List.of(ADDRESS, EMPLOYMENT));

    private static final ContainerDef ORGANIZATION = new ContainerDef("organization",
            List.of("display_name", "email", "website", "employee_count", "status",
                    "tags", "labels", "language", "organization_name"),
            List.of(ADDRESS, FINANCIALS));

    private static final ContainerDef PRODUCT = new ContainerDef("product",
            List.of("sku", "category", "brand", "manufacturer", "description", "status",
                    "tags", "labels", "rating", "app_version", "archived"),
            List.of(SPECS, INVENTORY));

    private static final ContainerDef SERVER = new ContainerDef("server",
            List.of("hostname", "fqdn", "api_version", "schema_version", "status",
                    "tags", "labels", "app_version"),
            List.of(HARDWARE, NETWORK));

    private static final ContainerDef EVENT = new ContainerDef("event",
            List.of("status", "tags", "labels", "event_count", "view_count", "ratio",
                    "score_range", "duration_ms", "campaign_window", "created_at", "updated_at",
                    "published_at", "expires_at", "event_period", "priority", "severity"),
            List.of(PAYLOAD));

    private static final List<ContainerDef> ROOT_CONTAINERS =
            List.of(PERSON, ORGANIZATION, PRODUCT, SERVER, EVENT);

    private static final List<String> LANG_CODES =
            List.of("en", "fr", "de", "es", "ru", "ar", "he", "zh");

    private static final List<String> FLATTENED_KEYS = List.of("color", "size", "region", "source", "channel");

    private static final long DATE_RANGE_DAYS = 365L;

    @Inject
    FieldCatalog catalog;

    @Inject
    ValueSourceRegistry valueSources;

    @Inject
    PhoneSampleGenerator phoneSampleGenerator;

    @Inject
    PhoneNumberNormalizer phoneNormalizer;

    public Map<String, Object> generate(GenerationParameters params, Random random) {
        int targetFields = randomBetween(params.minFields(), params.maxFields(), random);
        int depth = randomBetween(params.minDepth(), params.maxDepth(), random);
        // 80% single-language document, 20% multilingual
        String docLang = random.nextInt(10) < 8
                ? LANG_CODES.get(random.nextInt(LANG_CODES.size()))
                : "multilang";
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> fieldTargets = new HashMap<>();
        if (depth > 1) {
            buildStructure(root, ROOT_CONTAINERS, fieldTargets, depth - 1, random);
        }
        PhoneCatchallCollector phones = new PhoneCatchallCollector(phoneNormalizer);
        List<FieldDefinition> shuffled = new ArrayList<>(catalog.all());
        Collections.shuffle(shuffled, random);
        int placed = 0;
        for (FieldDefinition fd : shuffled) {
            if (placed >= targetFields) break;
            List<Map<String, Object>> targets = fieldTargets.get(fd.name());
            Map<String, Object> target = (targets == null || targets.isEmpty())
                    ? root : targets.get(random.nextInt(targets.size()));
            if (!target.containsKey(fd.name())) {
                target.put(fd.name(), generateValue(fd, docLang, random, phones));
                placed++;
            }
        }
        phones.applyTo(root);
        return root;
    }

    private void buildStructure(Map<String, Object> parentMap, List<ContainerDef> containers,
            Map<String, List<Map<String, Object>>> fieldTargets, int remainingDepth, Random random) {
        for (ContainerDef container : containers) {
            if (random.nextBoolean()) {
                Map<String, Object> containerMap = new LinkedHashMap<>();
                parentMap.put(container.name(), containerMap);
                for (String fieldName : container.fields()) {
                    fieldTargets.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(containerMap);
                }
                if (remainingDepth > 1 && !container.children().isEmpty()) {
                    buildStructure(containerMap, container.children(), fieldTargets, remainingDepth - 1, random);
                }
            }
        }
    }

    private int randomBetween(int min, int max, Random random) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }

    private Object generateValue(FieldDefinition fd, String docLang, Random random,
                                 PhoneCatchallCollector phones) {
        String source = fd.source();
        if (source.startsWith("file:")) {
            List<String> values = valueSources.get(source.substring("file:".length()));
            return values.get(random.nextInt(values.size()));
        }
        if (source.startsWith("email:")) {
            List<String> values = valueSources.get(source.substring("email:".length()));
            return values.get(random.nextInt(values.size()));
        }
        if (source.startsWith("langtext:")) {
            String base = source.substring("langtext:".length());
            List<String> values = valueSources.get(base + "_" + docLang + ".csv");
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
        if ("phone:random".equals(source)) {
            PhoneSample sample = phoneSampleGenerator.generate(docLang, random);
            phones.add(sample);
            return sample.value();
        }
        if ("geo:lat".equals(source)) {
            return Math.round((random.nextDouble() * 180.0 - 90.0) * 10000.0) / 10000.0;
        }
        if ("geo:lon".equals(source)) {
            return Math.round((random.nextDouble() * 360.0 - 180.0) * 10000.0) / 10000.0;
        }
        if ("port:random".equals(source)) {
            return 1 + random.nextInt(65535);
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
