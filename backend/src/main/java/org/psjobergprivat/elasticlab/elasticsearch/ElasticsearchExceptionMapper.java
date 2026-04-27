package org.psjobergprivat.elasticlab.elasticsearch;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Provider
public class ElasticsearchExceptionMapper implements ExceptionMapper<ElasticsearchException> {

    private static final Logger LOG = Logger.getLogger(ElasticsearchExceptionMapper.class);

    @Override
    public Response toResponse(ElasticsearchException exception) {
        ErrorCause top = exception.error();
        String message = buildMessage(exception.endpointId(), top);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        if (top != null) {
            if (top.type() != null) body.put("type", top.type());
            if (top.reason() != null) body.put("reason", top.reason());
            List<Map<String, String>> rootCauses = collectCauses(top);
            if (!rootCauses.isEmpty()) body.put("rootCauses", rootCauses);
        }

        int status = exception.status() > 0 ? exception.status() : Response.Status.BAD_REQUEST.getStatusCode();
        LOG.warnf("Elasticsearch [%s] returned %d: %s", exception.endpointId(), status, message);
        return Response.status(status)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String buildMessage(String endpointId, ErrorCause top) {
        if (top == null) {
            return "Elasticsearch request [" + endpointId + "] failed";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Elasticsearch [").append(endpointId).append("] failed: [")
                .append(top.type()).append("] ").append(top.reason());

        ErrorCause deepest = deepestCause(top);
        if (deepest != null && deepest != top) {
            sb.append(" — caused by [").append(deepest.type()).append("] ").append(deepest.reason());
        }
        return sb.toString();
    }

    private List<Map<String, String>> collectCauses(ErrorCause top) {
        List<Map<String, String>> out = new ArrayList<>();
        if (top.rootCause() != null) {
            for (ErrorCause rc : top.rootCause()) {
                out.add(Map.of(
                        "type", rc.type() != null ? rc.type() : "",
                        "reason", rc.reason() != null ? rc.reason() : ""));
            }
        }
        ErrorCause c = top.causedBy();
        while (c != null) {
            out.add(Map.of(
                    "type", c.type() != null ? c.type() : "",
                    "reason", c.reason() != null ? c.reason() : ""));
            c = c.causedBy();
        }
        return out;
    }

    private ErrorCause deepestCause(ErrorCause top) {
        ErrorCause current = top;
        if (current.causedBy() != null) {
            while (current.causedBy() != null) {
                current = current.causedBy();
            }
            return current;
        }
        if (current.rootCause() != null && !current.rootCause().isEmpty()) {
            return current.rootCause().get(0);
        }
        return null;
    }
}
