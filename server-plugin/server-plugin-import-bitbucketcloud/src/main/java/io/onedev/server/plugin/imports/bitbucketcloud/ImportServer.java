package io.onedev.server.plugin.imports.bitbucketcloud;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.http.client.utils.URIBuilder;
import org.apache.shiro.authz.UnauthorizedException;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import io.onedev.commons.bootstrap.SecretMasker;
import io.onedev.commons.utils.ExplicitException;
import io.onedev.commons.utils.StringUtils;
import io.onedev.commons.utils.TaskLogger;
import io.onedev.server.OneDev;
import io.onedev.server.annotation.ClassValidating;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.Password;
import io.onedev.server.data.migration.VersionedXmlDoc;
import io.onedev.server.entitymanager.AuditManager;
import io.onedev.server.entitymanager.BaseAuthorizationManager;
import io.onedev.server.entitymanager.ProjectManager;
import io.onedev.server.git.command.LsRemoteCommand;
import io.onedev.server.model.Project;
import io.onedev.server.persistence.TransactionManager;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.util.CollectionUtils;
import io.onedev.server.util.JerseyUtils;
import io.onedev.server.util.JerseyUtils.PageDataConsumer;
import io.onedev.server.validation.Validatable;
import io.onedev.server.web.component.taskbutton.TaskResult;
import io.onedev.server.web.component.taskbutton.TaskResult.PlainMessage;

@Editable
@ClassValidating
public class ImportServer implements Serializable, Validatable {

	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(ImportServer.class);
	
	private static final int PER_PAGE = 50;
	
	private String userName;
	
	private String appPassword;

	@Editable(order=10, name="Bitbucket Login Name")
	@NotEmpty
	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	@Editable(order=100, name="Bitbucket App Password", description="Bitbucket app password should be generated with "
			+ "permission <b>account/read</b>, <b>repositories/read</b> and <b>issues:read</b>")
	@Password
	@NotEmpty
	public String getAppPassword() {
		return appPassword;
	}

	public void setAppPassword(String appPassword) {
		this.appPassword = appPassword;
	}
	
	private String getApiEndpoint(String apiPath) {
		return "https://api.bitbucket.org/2.0/" + StringUtils.stripStart(apiPath, "/");
	}
	
	Map<String, String> listWorkspaces() {
		Map<String, String> workspaces = new HashMap<>();
		
		Client client = newClient();
		try {
			String apiEndpoint = getApiEndpoint("/user/permissions/workspaces");
			for (JsonNode valueElementNode: list(client, apiEndpoint, new TaskLogger() {

				@Override
				public void log(String message, String sessionId) {
					logger.info(message);
				}
				
			})) {
				JsonNode workspaceNode = valueElementNode.get("workspace");
				workspaces.put(workspaceNode.get("slug").asText(), workspaceNode.get("name").asText());
			}	
			CollectionUtils.sortByValue(workspaces);
		} catch (Exception e) {
			logger.error("Error listing workspaces", e);
		} finally {
			client.close();
		}
		
		return workspaces;
	}
	
	List<String> listRepositories(String workspaceId, boolean includeForks) {
		Client client = newClient();
		try {
			List<String> repositories = new ArrayList<>();
			
			String apiEndpoint = getApiEndpoint("/repositories/" + workspaceId);
			for (JsonNode repoNode: list(client, apiEndpoint, new TaskLogger() {

				@Override
				public void log(String message, String sessionId) {
					logger.info(message);
				}
				
			})) {
				if (includeForks || repoNode.get("parent") == null)
					repositories.add(repoNode.get("full_name").asText());
			}					
			
			return repositories;
		} finally {
			client.close();
		}
	}
	
	private List<JsonNode> list(Client client, String apiEndpoint, TaskLogger logger) {
		List<JsonNode> result = new ArrayList<>();
		list(client, apiEndpoint, new PageDataConsumer() {

			@Override
			public void consume(List<JsonNode> pageData) {
				result.addAll(pageData);
			}
			
		}, logger);
		return result;
	}
	
	private void list(Client client, String apiEndpoint, PageDataConsumer pageDataConsumer, 
			TaskLogger logger) {
		URI uri;
		try {
			uri = new URIBuilder(apiEndpoint)
					.addParameter("pagelen", String.valueOf(PER_PAGE)).build();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		
		while (true) {
			try {
				List<JsonNode> pageData = new ArrayList<>();
				JsonNode resultNode = JerseyUtils.get(client, uri.toString(), logger);
				if (resultNode.hasNonNull("values")) {
					for (JsonNode each: resultNode.get("values"))
						pageData.add(each);
					pageDataConsumer.consume(pageData);
				}
				if (resultNode.hasNonNull("next"))
					uri = new URIBuilder(resultNode.get("next").asText()).build();
				else
					break;
			} catch (URISyntaxException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	TaskResult importProjects(ImportRepositories repositories, ImportOption option, boolean dryRun, TaskLogger logger) {
		Client client = newClient();
		try {
			for (var bitbucketRepository : repositories.getImportRepositories()) {
				OneDev.getInstance(TransactionManager.class).run(() -> {
					try {
						String oneDevProjectPath;
						if (repositories.getParentOneDevProject() != null)
							oneDevProjectPath = repositories.getParentOneDevProject() + "/" + bitbucketRepository;
						else
							oneDevProjectPath = bitbucketRepository;

						logger.log("Importing from '" + bitbucketRepository + "' to '" + oneDevProjectPath + "'...");

						ProjectManager projectManager = OneDev.getInstance(ProjectManager.class);
						Project project = projectManager.setup(oneDevProjectPath);

						if (!project.isNew() && !SecurityUtils.canManageProject(project)) {
							throw new UnauthorizedException("Import target already exists. " +
									"You need to have project management privilege over it");
						}

						String apiEndpoint = getApiEndpoint("/repositories/" + bitbucketRepository);
						JsonNode repoNode = JerseyUtils.get(client, apiEndpoint, logger);

						project.setDescription(repoNode.get("description").asText(null));

						if (project.isNew() || project.getDefaultBranch() == null) {
							logger.log("Cloning code...");

							String cloneUrl = null;
							for (JsonNode cloneNode : repoNode.get("links").get("clone")) {
								if (cloneNode.get("name").asText().equals("https")) {
									cloneUrl = cloneNode.get("href").asText();
									break;
								}
							}
							if (cloneUrl == null)
								throw new ExplicitException("Https clone url not found");

							URIBuilder builder = new URIBuilder(cloneUrl);
							builder.setUserInfo(getUserName(), getAppPassword());

							SecretMasker.push(text -> StringUtils.replace(text, getAppPassword(), "******"));
							try {
								if (dryRun) {
									new LsRemoteCommand(builder.build().toString()).refs("HEAD").quiet(true).run();
								} else {
									if (project.isNew()) {
										projectManager.create(project);
										OneDev.getInstance(AuditManager.class).audit(project, "created project", null, VersionedXmlDoc.fromBean(project).toXML());
									}
									projectManager.clone(project, builder.build().toString());
								}
							} finally {
								SecretMasker.pop();
							}
						} else {
							logger.warning("Skipping code clone as the project already has code");
						}

						boolean isPrivate = repoNode.get("is_private").asBoolean();
						if (!isPrivate && !option.getPublicRoles().isEmpty())
							OneDev.getInstance(BaseAuthorizationManager.class).syncRoles(project, option.getPublicRoles());
					} catch (URISyntaxException e) {
						throw new RuntimeException(e);
					}
				});
			}
			return new TaskResult(true, new PlainMessage("Repositories imported successfully"));
		} finally {
			client.close();
		}
	}	

	private Client newClient() {
		Client client = ClientBuilder.newClient();
		client.property(ClientProperties.FOLLOW_REDIRECTS, true);
		client.register(HttpAuthenticationFeature.basic(getUserName(), getAppPassword()));
		return client;
	}
	
	@Override
	public boolean isValid(ConstraintValidatorContext context) {
		Client client = ClientBuilder.newClient();
		client.register(HttpAuthenticationFeature.basic(getUserName(), getAppPassword()));
		try {
			String apiEndpoint = getApiEndpoint("/user");
			WebTarget target = client.target(apiEndpoint);
			Invocation.Builder builder =  target.request();
			try (Response response = builder.get()) {
				if (response.getStatus() == 401) {
					context.disableDefaultConstraintViolation();
					String errorMessage = "Authentication failed";
					context.buildConstraintViolationWithTemplate(errorMessage).addConstraintViolation();
					return false;
				} else {
					String errorMessage = JerseyUtils.checkStatus(apiEndpoint, response);
					if (errorMessage != null) {
						context.disableDefaultConstraintViolation();
						context.buildConstraintViolationWithTemplate(errorMessage).addConstraintViolation();
						return false;
					} 
				}
			}
		} catch (Exception e) {
			context.disableDefaultConstraintViolation();
			String errorMessage = "Error connecting api service";
			if (e.getMessage() != null)
				errorMessage += ": " + e.getMessage();
			context.buildConstraintViolationWithTemplate(errorMessage).addConstraintViolation();
			return false;
		} finally {
			client.close();
		}
		return true;
	}
	
}
