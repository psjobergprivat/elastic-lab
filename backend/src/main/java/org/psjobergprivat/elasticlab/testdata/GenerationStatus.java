package org.psjobergprivat.elasticlab.testdata;

public record GenerationStatus(
        String state,
        int totalDocuments,
        int insertedDocuments,
        String error) {

    public static GenerationStatus idle() {
        return new GenerationStatus("idle", 0, 0, null);
    }

    public static GenerationStatus running(int total, int done) {
        return new GenerationStatus("running", total, done, null);
    }

    public static GenerationStatus completed(int total) {
        return new GenerationStatus("completed", total, total, null);
    }

    public static GenerationStatus failed(int total, int done, String error) {
        return new GenerationStatus("failed", total, done, error);
    }

    public boolean isRunning() {
        return "running".equals(state);
    }
}
