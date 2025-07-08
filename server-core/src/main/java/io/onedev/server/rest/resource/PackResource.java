package io.onedev.server.rest.resource;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.shiro.authz.UnauthorizedException;

import io.onedev.server.data.migration.VersionedXmlDoc;
import io.onedev.server.entitymanager.AuditManager;
import io.onedev.server.entitymanager.PackManager;
import io.onedev.server.model.Pack;
import io.onedev.server.model.PackBlob;
import io.onedev.server.model.PackBlobReference;
import io.onedev.server.model.PackLabel;
import io.onedev.server.rest.InvalidParamException;
import io.onedev.server.rest.annotation.Api;
import io.onedev.server.rest.resource.support.RestConstants;
import io.onedev.server.search.entity.pack.PackQuery;
import io.onedev.server.security.SecurityUtils;

@Api(name="Package")
@Path("/packages")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class PackResource {

	private final PackManager packManager;
	
	private final AuditManager auditManager;
	
	@Inject
	public PackResource(PackManager packManager, AuditManager auditManager) {
		this.packManager = packManager;
		this.auditManager = auditManager;
	}

	@Api(order=100)
	@Path("/{packId}")
    @GET
    public Pack getPack(@PathParam("packId") Long packId) {
		Pack pack = packManager.load(packId);
    	if (!SecurityUtils.canReadPack(pack.getProject())) 
			throw new UnauthorizedException();
    	return pack;
    }

	@Api(order=150, description = "Get list of <a href='/~help/api/io.onedev.server.rest.PackLabelResource'>labels</a>")
	@Path("/{packId}/labels")
	@GET
	public Collection<PackLabel> getLabels(@PathParam("packId") Long packId) {
		Pack pack = packManager.load(packId);
		if (!SecurityUtils.canReadPack(pack.getProject()))
			throw new UnauthorizedException();
		return pack.getLabels();
	}
	
	@Api(order=300)
	@Path("/{packId}/blobs")
    @GET
    public Collection<PackBlob> getBlobs(@PathParam("packId") Long packId) {
		Pack pack = packManager.load(packId);
    	if (!SecurityUtils.canReadPack(pack.getProject())) 
			throw new UnauthorizedException();
    	return pack.getBlobReferences().stream().map(PackBlobReference::getPackBlob).collect(toList());
    }
	
	@Api(order=600)
	@GET
    public List<Pack> queryPacks(
    		@QueryParam("query") @Api(description="Syntax of this query is the same as in <a href='/~packages'>packages page</a>", example="\"Type\" is \"Container Image\"") String query, 
    		@QueryParam("offset") @Api(example="0") int offset, 
    		@QueryParam("count") @Api(example="100") int count) {

		if (!SecurityUtils.isAdministrator() && count > RestConstants.MAX_PAGE_SIZE)
    		throw new InvalidParamException("Count should not be greater than " + RestConstants.MAX_PAGE_SIZE);

    	PackQuery parsedQuery;
		try {
			parsedQuery = PackQuery.parse(null, query, true);
		} catch (Exception e) {
			throw new InvalidParamException("Error parsing query", e);
		}
    	
    	return packManager.query(null, parsedQuery, false, offset, count);
    }
	
	@Api(order=700)
	@Path("/{packId}")
    @DELETE
    public Response deletePack(@PathParam("packId") Long packId) {
    	Pack pack = packManager.load(packId);
    	if (!SecurityUtils.canWritePack(pack.getProject()))
			throw new UnauthorizedException();
    	packManager.delete(pack);
		var oldAuditContent = VersionedXmlDoc.fromBean(pack).toXML();
		auditManager.audit(pack.getProject(), "deleted package \"" + pack.getReference(false) + "\" via RESTful API", oldAuditContent, null);
    	return Response.ok().build();
    }
	
}
