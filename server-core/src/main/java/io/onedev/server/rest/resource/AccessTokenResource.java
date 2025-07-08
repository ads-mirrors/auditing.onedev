package io.onedev.server.rest.resource;

import static io.onedev.server.security.SecurityUtils.getAuthUser;
import static io.onedev.server.security.SecurityUtils.isAdministrator;

import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.shiro.authz.UnauthorizedException;

import io.onedev.commons.utils.ExplicitException;
import io.onedev.server.data.migration.VersionedXmlDoc;
import io.onedev.server.entitymanager.AccessTokenManager;
import io.onedev.server.entitymanager.AuditManager;
import io.onedev.server.model.AccessToken;
import io.onedev.server.model.AccessTokenAuthorization;
import io.onedev.server.rest.annotation.Api;

@Path("/access-tokens")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class AccessTokenResource {
	
	private final AccessTokenManager accessTokenManager;

	private final AuditManager auditManager;
	
	@Inject
	public AccessTokenResource(AccessTokenManager accessTokenManager, AuditManager auditManager) {
		this.accessTokenManager = accessTokenManager;
		this.auditManager = auditManager;
	}

	@Api(order=100)
	@Path("/{accessTokenId}")
	@GET
	public AccessToken getToken(@PathParam("accessTokenId") Long accessTokenId) {
		var accessToken = accessTokenManager.load(accessTokenId);
    	if (!isAdministrator() && !accessToken.getOwner().equals(getAuthUser())) 
			throw new UnauthorizedException();
    	return accessToken;
	}

	@Api(order=150)
	@Path("/{accessTokenId}/authorizations")
	@GET
	public Collection<AccessTokenAuthorization> getAuthorizations(@PathParam("accessTokenId") Long accessTokenId) {
		var accessToken = accessTokenManager.load(accessTokenId);
		if (!isAdministrator() && !accessToken.getOwner().equals(getAuthUser()))
			throw new UnauthorizedException();
		return accessToken.getAuthorizations();
	}
	
	@Api(order=200, description="Create access token")
	@POST
	public Long createToken(@NotNull @Valid AccessToken accessToken) {
		var owner = accessToken.getOwner();
		if (!isAdministrator() && !owner.equals(getAuthUser()))
			throw new UnauthorizedException();
		else if (owner.isDisabled())
			throw new ExplicitException("Can not create access token for disabled user");
		
		if (accessTokenManager.findByOwnerAndName(owner, accessToken.getName()) != null)
			throw new ExplicitException("Name already used by another access token of the owner");
			
		accessTokenManager.createOrUpdate(accessToken);

		if (!getAuthUser().equals(owner)) {
			var newAuditContent = VersionedXmlDoc.fromBean(accessToken.getFacade()).toXML();
			auditManager.audit(null, "created access token \"" + accessToken.getName() + "\" for account \"" + owner.getName() + "\" via RESTful API", 
					null, newAuditContent);
		}

		return accessToken.getId();
	}

	@Api(order=250, description="Update access token")
	@Path("/{accessTokenId}")
	@POST
	public Response updateToken(@PathParam("accessTokenId") Long accessTokenId, @NotNull @Valid AccessToken accessToken) {
		var owner = accessToken.getOwner();
		if (!isAdministrator() && !owner.equals(getAuthUser()))
			throw new UnauthorizedException();

		var accessTokenWithSameName = accessTokenManager.findByOwnerAndName(owner, accessToken.getName());
		if (accessTokenWithSameName != null && !accessTokenWithSameName.equals(accessToken))
			throw new BadRequestException("Name already used by another access token of the owner");
		
		accessTokenManager.createOrUpdate(accessToken);

		if (!getAuthUser().equals(owner)) {
			var oldAuditContent = accessToken.getOldVersion().toXML();
			var newAuditContent = VersionedXmlDoc.fromBean(accessToken).toXML();
			auditManager.audit(null, "changed access token \"" + accessToken.getName() + "\" for account \"" + owner.getName() + "\" via RESTful API", 
					oldAuditContent, newAuditContent);
		}
		return Response.ok().build();
	}
	
	@Api(order=300)
	@Path("/{accessTokenId}")
	@DELETE
	public Response deleteToken(@PathParam("accessTokenId") Long accessTokenId) {
		var accessToken = accessTokenManager.load(accessTokenId);
		if (!isAdministrator() && !accessToken.getOwner().equals(getAuthUser())) 
			throw new UnauthorizedException();
		accessTokenManager.delete(accessToken);

		if (!getAuthUser().equals(accessToken.getOwner())) {
			var oldAuditContent = VersionedXmlDoc.fromBean(accessToken.getFacade()).toXML();
			auditManager.audit(null, "deleted access token \"" + accessToken.getName() + "\" for account \"" + accessToken.getOwner().getName() + "\" via RESTful API", 
					oldAuditContent, null);
		}
		return Response.ok().build();
	}

}
