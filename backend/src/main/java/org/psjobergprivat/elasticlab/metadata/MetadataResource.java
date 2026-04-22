package org.psjobergprivat.elasticlab.metadata;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.util.List;

@Path("/metadata")
@Produces(MediaType.APPLICATION_JSON)
public class MetadataResource {

    @Inject
    IndexMetadataService indexMetadataService;

    @GET
    @Path("/server")
    public ServerMetadata getServerMetadata() throws IOException {
        return indexMetadataService.loadServerMetadata();
    }

    @GET
    @Path("/indices")
    public List<String> listIndices() throws IOException {
        return indexMetadataService.listIndexNames();
    }

    @GET
    @Path("/indices/{indexName}")
    public IndexMetadata getIndexMetadata(@PathParam("indexName") String indexName) throws IOException {
        return indexMetadataService.loadMetadata(indexName);
    }
}
