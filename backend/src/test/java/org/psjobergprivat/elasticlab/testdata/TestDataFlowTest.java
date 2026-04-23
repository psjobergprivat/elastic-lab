package org.psjobergprivat.elasticlab.testdata;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(ElasticsearchTestResource.class)
@TestMethodOrder(OrderAnnotation.class)
class TestDataFlowTest {

    private static final int DOCUMENTS_TO_GENERATE = 50;

    @Test
    @Order(1)
    void previewProducesDocumentWithFields() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "numDocuments", 1,
                        "minFields", 5,
                        "maxFields", 8,
                        "minDepth", 2,
                        "maxDepth", 3))
                .when()
                .post("/api/data/preview")
                .then()
                .statusCode(200)
                .body("$", aMapWithSize(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(2)
    void deleteAllOnEmptyIndexIsSafe() {
        given()
                .when()
                .delete("/api/data")
                .then()
                .statusCode(200)
                .body("deleted", equalTo(0));
    }

    @Test
    @Order(3)
    void generateInsertsDocumentsAndReachesCompleted() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "numDocuments", DOCUMENTS_TO_GENERATE,
                        "minFields", 4,
                        "maxFields", 8,
                        "minDepth", 1,
                        "maxDepth", 2))
                .when()
                .post("/api/data/generate")
                .then()
                .statusCode(200)
                .body("state", equalTo("running"));

        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> "completed".equals(given()
                        .when().get("/api/data/generate/status")
                        .then().statusCode(200)
                        .extract().path("state")));

        given()
                .when()
                .get("/api/data")
                .then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(DOCUMENTS_TO_GENERATE));
    }

    @Test
    @Order(4)
    void mappingHonorsCatalogFieldTypes() {
        JsonPath mapping = given()
                .when()
                .get("/api/metadata/indices/elastic-lab-test")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        List<Map<String, Object>> templates = mapping.getList("mappings.dynamic_templates");
        assertNotNull(templates, "dynamic_templates should be present in the mapping");
        assertThat(templateMatchesWith(templates, "field_email", "email", "keyword"), equalTo(true));
        assertThat(templateMatchesWith(templates, "field_client_ip", "client_ip", "ip"), equalTo(true));
        assertThat(templateMatchesWith(templates, "field_app_version", "app_version", "version"), equalTo(true));
        assertThat(templateMatchesWith(templates, "field_attachment", "attachment", "binary"), equalTo(true));
        assertThat(templateMatchesWith(templates, "field_attributes", "attributes", "flattened"), equalTo(true));
        assertThat(templateMatchesWith(templates, "field_duration_ms", "duration_ms", "long_range"), equalTo(true));
        assertThat(templateMatchesWith(templates, "field_event_period", "event_period", "date_range"), equalTo(true));
        assertThat(templateMatchesWith(templates, "field_relation", "relation", "join"), equalTo(true));
        assertThat(templateMatchesWith(templates, "field_account_type", "account_type", "constant_keyword"), equalTo(true));

        Map<String, Object> nestedTemplate = findTemplate(templates, "nested_containers");
        assertNotNull(nestedTemplate, "nested_containers template should exist");
        assertThat(((Map<?, ?>) nestedTemplate.get("mapping")).get("type"), equalTo("nested"));

        Map<String, Object> properties = mapping.getMap("mappings.properties");
        if (properties != null) {
            assertResolvedTypeIfPresent(properties, "client_ip", "ip");
            assertResolvedTypeIfPresent(properties, "app_version", "version");
            assertResolvedTypeIfPresent(properties, "duration_ms", "long_range");
            assertResolvedTypeIfPresent(properties, "relation", "join");
            assertResolvedTypeIfPresent(properties, "attributes", "flattened");
        }
    }

    @Test
    @Order(5)
    void deleteAllRemovesEveryDocument() {
        given()
                .when()
                .delete("/api/data")
                .then()
                .statusCode(200)
                .body("deleted", greaterThanOrEqualTo(DOCUMENTS_TO_GENERATE));

        given()
                .when()
                .get("/api/data")
                .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    @SuppressWarnings("unchecked")
    private static boolean templateMatchesWith(List<Map<String, Object>> templates, String templateName,
                                               String expectedMatch, String expectedType) {
        Map<String, Object> template = findTemplate(templates, templateName);
        if (template == null) {
            return false;
        }
        if (!matchesScalarOrList(template.get("match"), expectedMatch)) {
            return false;
        }
        Map<String, Object> mapping = (Map<String, Object>) template.get("mapping");
        return mapping != null && expectedType.equals(mapping.get("type"));
    }

    private static boolean matchesScalarOrList(Object value, String expected) {
        if (expected.equals(value)) {
            return true;
        }
        return value instanceof List<?> list && list.size() == 1 && expected.equals(list.get(0));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findTemplate(List<Map<String, Object>> templates, String name) {
        for (Map<String, Object> entry : templates) {
            if (entry.containsKey(name)) {
                return (Map<String, Object>) entry.get(name);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void assertResolvedTypeIfPresent(Map<String, Object> properties, String fieldName, String expectedType) {
        Object def = properties.get(fieldName);
        if (def == null) {
            return;
        }
        Map<String, Object> defMap = (Map<String, Object>) def;
        assertTrue(expectedType.equals(defMap.get("type")),
                "Resolved field '" + fieldName + "' expected type " + expectedType + " but was " + defMap.get("type"));
    }
}
