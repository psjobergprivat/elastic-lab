package org.psjobergprivat.elasticlab.testdata;

public record GenerationParameters(
        int numDocuments,
        int minFields,
        int maxFields,
        int minDepth,
        int maxDepth) {

    public GenerationParameters {
        if (numDocuments < 1 || numDocuments > 10_000_000) {
            throw new IllegalArgumentException("numDocuments must be between 1 and 10000000");
        }
        if (minFields < 1 || maxFields > 50 || minFields > maxFields) {
            throw new IllegalArgumentException("fields range must be within 1..50 and min <= max");
        }
        if (minDepth < 1 || maxDepth > 5 || minDepth > maxDepth) {
            throw new IllegalArgumentException("depth range must be within 1..5 and min <= max");
        }
    }
}
