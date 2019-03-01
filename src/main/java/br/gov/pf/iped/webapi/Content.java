package br.gov.pf.iped.webapi;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.sleuthkit.datamodel.TskCoreException;

import dpf.sp.gpinf.indexer.search.IPEDSource;
import gpinf.dev.data.EvidenceFile;

@Path("sources/{sourceID}/docs/{id}/content")
public class Content {

	@GET
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response content(
			@PathParam("sourceID") int sourceID,
			@PathParam("id") int id)
					throws TskCoreException, IOException, URISyntaxException{

		IPEDSource source = Sources.get(sourceID);
    	final EvidenceFile item = source.getItemByID(id);
		return Response.status(200)
				.header("Content-Length", String.valueOf(item.getLength()))
				.header("Content-Disposition", "attachment; filename=\""+ item.getName() + "\"")
				.entity(new StreamingOutput() {
					@Override
					public void write(OutputStream arg0) throws IOException, WebApplicationException {
						IOUtils.copy(item.getStream(), arg0);
					}})
				.build();
	}
}
