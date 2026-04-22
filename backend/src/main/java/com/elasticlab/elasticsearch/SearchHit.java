package com.elasticlab.elasticsearch;

import java.util.Map;

public record SearchHit(String id, Double score, Map<String, Object> source) {
}
