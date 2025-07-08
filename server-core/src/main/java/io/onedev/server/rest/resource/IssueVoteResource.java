package io.onedev.server.rest.resource;

import io.onedev.server.entitymanager.IssueVoteManager;
import io.onedev.server.model.IssueVote;
import io.onedev.server.rest.annotation.Api;
import io.onedev.server.security.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static io.onedev.server.security.SecurityUtils.canModifyOrDelete;

@Path("/issue-votes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class IssueVoteResource {

	private final IssueVoteManager voteManager;

	@Inject
	public IssueVoteResource(IssueVoteManager voteManager) {
		this.voteManager = voteManager;
	}

	@Api(order=100)
	@Path("/{voteId}")
	@GET
	public IssueVote getVote(@PathParam("voteId") Long voteId) {
		IssueVote vote = voteManager.load(voteId);
		if (!SecurityUtils.canAccessIssue(vote.getIssue()))
			throw new UnauthorizedException();
		return vote;
	}
	
	@Api(order=200, description="Create new issue vote")
	@POST
	public Long createVote(@NotNull IssueVote vote) {
		if (!SecurityUtils.canAccessIssue(vote.getIssue())
				|| !SecurityUtils.isAdministrator() && !vote.getUser().equals(SecurityUtils.getAuthUser())) {
			throw new UnauthorizedException();
		}
		voteManager.create(vote);
		return vote.getId();
	}
	
	@Api(order=300)
	@Path("/{voteId}")
	@DELETE
	public Response deleteVote(@PathParam("voteId") Long voteId) {
		IssueVote vote = voteManager.load(voteId);
		if (!canModifyOrDelete(vote)) 
			throw new UnauthorizedException();
		
		voteManager.delete(vote);
		return Response.ok().build();
	}
	
}
