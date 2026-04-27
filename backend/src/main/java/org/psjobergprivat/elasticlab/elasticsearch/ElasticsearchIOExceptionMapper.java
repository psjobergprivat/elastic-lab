package org.psjobergprivat.elasticlab.elasticsearch;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

@Provider
public class ElasticsearchIOExceptionMapper implements ExceptionMapper<IOException> {

    private static final Logger LOG = Logger.getLogger(ElasticsearchIOExceptionMapper.class);

    @ConfigProperty(name = "quarkus.elasticsearch.hosts")
    List<String> elasticsearchHosts;

    @Override
    public Response toResponse(IOException exception) {
        Throwable unreachableCause = findUnreachableCause(exception);
        if (unreachableCause != null) {
            String hosts = String.join(", ", elasticsearchHosts);
            String detail = unreachableCause.getMessage() != null ? unreachableCause.getMessage() : unreachableCause.getClass().getSimpleName();
            String message = "Cannot reach Elasticsearch at " + hosts
                    + ". Verify that the server is running and reachable from the backend (" + detail + ").";
            LOG.warnf("Elasticsearch unreachable at %s: %s", hosts, detail);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", message))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        LOG.error("I/O error while talking to Elasticsearch", exception);
        String message = exception.getMessage() != null ? exception.getMessage() : "Internal server error";
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Throwable findUnreachableCause(Throwable t) {
        while (t != null) {
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return t;
            }
            t = t.getCause();
        }
        return null;
    }
}
