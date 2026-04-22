package org.psjobergprivat.elasticlab.elasticsearch;

import java.util.List;

public record SearchHits(long total, List<SearchHit> hits) {
}
