package io.onedev.server.plugin.imports.gitea;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;
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
import org.glassfish.jersey.client.oauth2.OAuth2ClientSupport;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unbescape.html.HtmlEscape;

import com.fasterxml.jackson.databind.JsonNode;

import edu.emory.mathcs.backport.java.util.Collections;
import io.onedev.commons.bootstrap.SecretMasker;
import io.onedev.commons.utils.ExplicitException;
import io.onedev.commons.utils.StringUtils;
import io.onedev.commons.utils.TaskLogger;
import io.onedev.server.OneDev;
import io.onedev.server.annotation.ClassValidating;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.Password;
import io.onedev.server.buildspecmodel.inputspec.InputSpec;
import io.onedev.server.data.migration.VersionedXmlDoc;
import io.onedev.server.entitymanager.AuditManager;
import io.onedev.server.entitymanager.BaseAuthorizationManager;
import io.onedev.server.entitymanager.IssueManager;
import io.onedev.server.entitymanager.IterationManager;
import io.onedev.server.entitymanager.ProjectManager;
import io.onedev.server.entitymanager.SettingManager;
import io.onedev.server.entitymanager.UserManager;
import io.onedev.server.entityreference.ReferenceMigrator;
import io.onedev.server.event.ListenerRegistry;
import io.onedev.server.event.project.issue.IssuesImported;
import io.onedev.server.git.command.LsRemoteCommand;
import io.onedev.server.model.Issue;
import io.onedev.server.model.IssueComment;
import io.onedev.server.model.IssueField;
import io.onedev.server.model.IssueSchedule;
import io.onedev.server.model.Iteration;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.model.support.LastActivity;
import io.onedev.server.model.support.administration.GlobalIssueSetting;
import io.onedev.server.model.support.issue.field.spec.FieldSpec;
import io.onedev.server.persistence.TransactionManager;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.util.DateUtils;
import io.onedev.server.util.JerseyUtils;
import io.onedev.server.util.JerseyUtils.PageDataConsumer;
import io.onedev.server.util.Pair;
import io.onedev.server.validation.Validatable;
import io.onedev.server.web.component.taskbutton.TaskResult;
import io.onedev.server.web.component.taskbutton.TaskResult.HtmlMessgae;

@Editable
@ClassValidating
public class ImportServer implements Serializable, Validatable {

	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(ImportServer.class);
	
	private static final int PER_PAGE = 50;
	
	private static final String PROP_API_URL = "apiUrl";
	
	private static final String PROP_ACCESS_TOKEN = "accessToken";
	
	private String apiUrl;
	
	private String accessToken;

	@Editable(order=10, name="Gitea API URL", description="Specify Gitea API url, for instance <tt>https://gitea.example.com/api/v1</tt>")
	@NotEmpty
	public String getApiUrl() {
		return apiUrl;
	}

	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	@Editable(order=100, name="Gitea Personal Access Token")
	@Password
	@NotEmpty
	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}
	
	private String getApiEndpoint(String apiPath) {
		return StringUtils.stripEnd(apiUrl, "/") + "/" + StringUtils.stripStart(apiPath, "/");
	}

	List<String> listOrganizations() {
		List<String> organizations = new ArrayList<>();
		
		Client client = newClient();
		try {
			String apiEndpoint = getApiEndpoint("/user/orgs");
			for (JsonNode orgNode: list(client, apiEndpoint, new TaskLogger() {

				@Override
				public void log(String message, String sessionId) {
					logger.info(message);
				}
				
			})) {
				organizations.add(orgNode.get("username").asText());
			}	
			Collections.sort(organizations);
		} catch (Exception e) {
			logger.error("Error listing organizations", e);
		} finally {
			client.close();
		}
		
		return organizations;
	}
	
	List<String> listRepositories(@Nullable String organization, boolean includeForks) {
		Client client = newClient();
		try {
			String apiEndpoint;
			if (organization != null) 
				apiEndpoint = getApiEndpoint("/orgs/" + organization + "/repos");
			else 
				apiEndpoint = getApiEndpoint("/user/repos");
			List<String> repositories = new ArrayList<>();
			for (JsonNode repoNode: list(client, apiEndpoint, new TaskLogger() {

				@Override
				public void log(String message, String sessionId) {
					logger.info(message);
				}
				
			})) {
				String repoName = repoNode.get("name").asText();
				JsonNode ownerNode = repoNode.get("owner");
				String ownerName = ownerNode.get("login").asText();
				JsonNode emailNode = ownerNode.get("email");
				if ((organization != null || emailNode != null && StringUtils.isNotBlank(emailNode.asText()))
						&& (includeForks || !repoNode.get("fork").asBoolean())) {
					repositories.add(ownerName + "/" + repoName);
				}
			}					
			Collections.sort(repositories);
			return repositories;
		} finally {
			client.close();
		}
		
	}
	
	IssueImportOption buildIssueImportOption(Collection<String> repositories) {
		IssueImportOption option = new IssueImportOption();
		Client client = newClient();
		try {
			TaskLogger taskLogger = new TaskLogger() {

				@Override
				public void log(String message, String sessionId) {
					logger.info(message);
				}
				
			};
			Set<String> labels = new LinkedHashSet<>();
			for (String repo: repositories) {
				String apiEndpoint = getApiEndpoint("/repos/" + repo + "/labels"); 
				for (JsonNode labelNode: list(client, apiEndpoint, taskLogger)) 
					labels.add(labelNode.get("name").asText());
				try {
					apiEndpoint = getApiEndpoint("/orgs/" + StringUtils.substringBefore(repo, "/") + "/labels");
					for (JsonNode labelNode: list(client, apiEndpoint, taskLogger)) 
						labels.add(labelNode.get("name").asText());
				} catch (Exception e) {
					// ignore as exception might be thrown if repo belongs to a user account
				}
			}
			
			for (String label: labels) {
				IssueLabelMapping mapping = new IssueLabelMapping();
				mapping.setGiteaIssueLabel(label);
				option.getIssueLabelMappings().add(mapping);
			}
		} finally {
			client.close();
		}
		return option;
	}
	
	@Nullable
	private Long getUserId(Map<String, Optional<Long>> users, JsonNode userNode, TaskLogger logger) {
		String login = userNode.get("login").asText();
		Optional<Long> userIdOpt = users.get(login);
		if (userIdOpt == null) {
			String email = null;
			if (userNode.hasNonNull("email"))
				email = userNode.get("email").asText(null);
			if (email != null)
				userIdOpt = Optional.ofNullable(User.idOf(OneDev.getInstance(UserManager.class).findByVerifiedEmailAddress(email)));
			else
				userIdOpt = Optional.empty();
			users.put(login, userIdOpt);
		}
		return userIdOpt.orElse(null);
	}
	
	ImportResult importIssues(String giteaRepo, Project oneDevProject, IssueImportOption option, 
			Map<String, Optional<Long>> userIds, boolean dryRun, TaskLogger logger) {
		IssueManager issueManager = OneDev.getInstance(IssueManager.class);
		Client client = newClient();
		try {
			Set<String> nonExistentIterations = new HashSet<>();
			Set<String> nonExistentLogins = new HashSet<>();
			Set<String> unmappedIssueLabels = new HashSet<>();
			
			Map<String, Pair<FieldSpec, String>> labelMappings = new HashMap<>();
			Map<String, Iteration> iterationMappings = new HashMap<>();
			
			for (IssueLabelMapping mapping: option.getIssueLabelMappings()) {
				String oneDevFieldName = StringUtils.substringBefore(mapping.getOneDevIssueField(), "::");
				String oneDevFieldValue = StringUtils.substringAfter(mapping.getOneDevIssueField(), "::");
				FieldSpec fieldSpec = getIssueSetting().getFieldSpec(oneDevFieldName);
				if (fieldSpec == null)
					throw new ExplicitException("No field spec found: " + oneDevFieldName);
				labelMappings.put(mapping.getGiteaIssueLabel(), new Pair<>(fieldSpec, oneDevFieldValue));
			}
			
			for (Iteration iteration: oneDevProject.getIterations())
				iterationMappings.put(iteration.getName(), iteration);
			
			String initialIssueState = getIssueSetting().getInitialStateSpec().getName();
				
			List<Issue> issues = new ArrayList<>();
			
			Map<Long, Long> issueNumberMappings = new HashMap<>();
			
			AtomicInteger numOfImportedIssues = new AtomicInteger(0);
			PageDataConsumer pageDataConsumer = new PageDataConsumer() {

				private String joinAsMultilineHtml(List<String> values) {
					List<String> escapedValues = new ArrayList<>();
					for (String value: values)
						escapedValues.add(HtmlEscape.escapeHtml5(value));
					return StringUtils.join(escapedValues, "<br>");
				}
				
				@Override
				public void consume(List<JsonNode> pageData) throws InterruptedException {
					for (JsonNode issueNode: pageData) {
						if (Thread.interrupted())
							throw new InterruptedException();

						Map<String, String> extraIssueInfo = new LinkedHashMap<>();
						
						Issue issue = new Issue();
						issue.setProject(oneDevProject);
						issue.setTitle(issueNode.get("title").asText());
						issue.setDescription(issueNode.get("body").asText(null));
						
						issue.setNumberScope(oneDevProject.getForkRoot());

						Long newNumber;
						Long oldNumber = issueNode.get("number").asLong();
						if (dryRun || (issueManager.find(oneDevProject, oldNumber) == null && !issueNumberMappings.containsValue(oldNumber))) 
							newNumber = oldNumber;
						else
							newNumber = issueManager.getNextNumber(oneDevProject);
						
						issue.setNumber(newNumber);
						issueNumberMappings.put(oldNumber, newNumber);
						
						String issueFQN = giteaRepo + "#" + oldNumber;
						
						if (issueNode.get("state").asText().equals("closed"))
							issue.setState(option.getClosedIssueState());
						else
							issue.setState(initialIssueState);
						
						if (issueNode.hasNonNull("milestone")) {
							String milestoneName = issueNode.get("milestone").get("title").asText();
							Iteration iteration = iterationMappings.get(milestoneName);
							if (iteration != null) {
								IssueSchedule schedule = new IssueSchedule();
								schedule.setIssue(issue);
								schedule.setIteration(iteration);
								issue.getSchedules().add(schedule);
							} else {
								extraIssueInfo.put("Milestone", milestoneName);
								nonExistentIterations.add(milestoneName);
							}
						}
						
						var userManager = OneDev.getInstance(UserManager.class);
						JsonNode userNode = issueNode.get("user");
						Long userId = getUserId(userIds, userNode, logger);
						if (userId != null) {
							issue.setSubmitter(userManager.load(userId));
						} else {
							issue.setSubmitter(OneDev.getInstance(UserManager.class).getUnknown());
							nonExistentLogins.add(userNode.get("login").asText());
						}
						
						String created_at = issueNode.get("created_at").asText();
						issue.setSubmitDate(Date.from(Instant.from(
								DateTimeFormatter.ISO_DATE_TIME.parse(created_at))));
						
						LastActivity lastActivity = new LastActivity();
						lastActivity.setDescription("Opened");
						lastActivity.setDate(issue.getSubmitDate());
						lastActivity.setUser(issue.getSubmitter());
						issue.setLastActivity(lastActivity);
						
						List<JsonNode> assigneeNodes = new ArrayList<>();
						if (issueNode.hasNonNull("assignees")) {
							for (JsonNode assigneeNode: issueNode.get("assignees")) 
								assigneeNodes.add(assigneeNode);
						} else if (issueNode.hasNonNull("assignee")) {
							assigneeNodes.add(issueNode.get("assignee"));
						}
						
						for (JsonNode assigneeNode: assigneeNodes) {
							IssueField assigneeField = new IssueField();
							assigneeField.setIssue(issue);
							assigneeField.setName(option.getAssigneesIssueField());
							assigneeField.setType(InputSpec.USER);
							
							userId = getUserId(userIds, assigneeNode, logger);
							if (userId != null) { 
								assigneeField.setValue(userManager.load(userId).getName());
								issue.getFields().add(assigneeField);
							} else {
								nonExistentLogins.add(assigneeNode.get("login").asText());
							}
						}

						if (option.getDueDateIssueField() != null) {
							String dueDate = issueNode.get("due_date").asText(null);
							if (dueDate != null) {
								IssueField issueField = new IssueField();
								issueField.setIssue(issue);
								issueField.setName(option.getDueDateIssueField());
								issueField.setType(InputSpec.DATE);
								issueField.setValue(DateUtils.formatDate(Date.from(
										Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(dueDate)))));
								issue.getFields().add(issueField);
							}
						}
						
						List<String> currentUnmappedLabels = new ArrayList<>();
						for (JsonNode labelNode: issueNode.get("labels")) {
							String labelName = labelNode.get("name").asText();
							Pair<FieldSpec, String> mapped = labelMappings.get(labelName);
							if (mapped != null) {
								IssueField labelField = new IssueField();
								labelField.setIssue(issue);
								labelField.setName(mapped.getLeft().getName());
								labelField.setType(InputSpec.ENUMERATION);
								labelField.setValue(mapped.getRight());
								labelField.setOrdinal(mapped.getLeft().getOrdinal(mapped.getRight()));
								issue.getFields().add(labelField);
							} else {
								currentUnmappedLabels.add(labelName);
								unmappedIssueLabels.add(HtmlEscape.escapeHtml5(labelName));
							}
						}

						if (!currentUnmappedLabels.isEmpty()) 
							extraIssueInfo.put("Labels", joinAsMultilineHtml(currentUnmappedLabels));
						
						String apiEndpoint = getApiEndpoint("/repos/" + giteaRepo  
								+ "/issues/" + oldNumber + "/comments");
						for (JsonNode commentNode: list(client, apiEndpoint, logger)) {
							String commentContent = commentNode.get("body").asText(null); 
							if (StringUtils.isNotBlank(commentContent)) {
								IssueComment comment = new IssueComment();
								comment.setIssue(issue);
								comment.setContent(commentContent);
								
								created_at = commentNode.get("created_at").asText();
								comment.setDate(Date.from(Instant.from(
										DateTimeFormatter.ISO_DATE_TIME.parse(created_at))));
								
								userNode = commentNode.get("user");
								userId = getUserId(userIds, userNode, logger);
								if (userId != null) {
									comment.setUser(userManager.load(userId));
								} else {
									comment.setUser(OneDev.getInstance(UserManager.class).getUnknown());
									nonExistentLogins.add(userNode.get("username").asText());
								}
								issue.getComments().add(comment);
							}
						}
						
						issue.setCommentCount(issue.getComments().size());

						Set<String> fieldAndValues = new HashSet<>();
						for (IssueField field: issue.getFields()) {
							String fieldAndValue = field.getName() + "::" + field.getValue();
							if (!fieldAndValues.add(fieldAndValue)) {
								String errorMessage = String.format(
										"Duplicate issue field mapping (issue: %s, field: %s)", 
										issueFQN, fieldAndValue);
								throw new ExplicitException(errorMessage);
							}
						}
						
						if (!extraIssueInfo.isEmpty()) {
							StringBuilder builder = new StringBuilder("|");
							for (String key: extraIssueInfo.keySet()) 
								builder.append(key).append("|");
							builder.append("\n|");
							extraIssueInfo.keySet().stream().forEach(it->builder.append("---|"));
							builder.append("\n|");
							for (String value: extraIssueInfo.values())
								builder.append(value).append("|");
							
							if (issue.getDescription() != null)
								issue.setDescription(builder.toString() + "\n\n" + issue.getDescription());
							else
								issue.setDescription(builder.toString());
						}
						issues.add(issue);
					}
					logger.log("Imported " + numOfImportedIssues.addAndGet(pageData.size()) + " issues");
				}
				
			};

			String apiEndpoint = getApiEndpoint("/repos/" + giteaRepo + "/issues?state=all&type=issues");
			list(client, apiEndpoint, pageDataConsumer, logger);

			if (!dryRun) {
				ReferenceMigrator migrator = new ReferenceMigrator(Issue.class, issueNumberMappings);
				Dao dao = OneDev.getInstance(Dao.class);
				for (Issue issue: issues) {
					if (issue.getDescription() != null) 
						issue.setDescription(migrator.migratePrefixed(issue.getDescription(), "#"));

					dao.persist(issue);
					for (IssueSchedule schedule: issue.getSchedules())
						dao.persist(schedule);
					for (IssueField field: issue.getFields())
						dao.persist(field);
					for (IssueComment comment: issue.getComments()) {
						comment.setContent(migrator.migratePrefixed(comment.getContent(),  "#"));
						dao.persist(comment);
					}
				}
			}
			
			ImportResult result = new ImportResult();
			result.nonExistentLogins.addAll(nonExistentLogins);
			result.nonExistentIterations.addAll(nonExistentIterations);
			result.unmappedIssueLabels.addAll(unmappedIssueLabels);
			result.issuesImported = !issues.isEmpty();
			
			if (!dryRun && !issues.isEmpty())
				OneDev.getInstance(ListenerRegistry.class).post(new IssuesImported(oneDevProject, issues));
			
			return result;
		} finally {
			if (!dryRun)
				issueManager.resetNextNumber(oneDevProject);
			client.close();
		}
	}
	
	TaskResult importProjects(ImportRepositories repositories, ProjectImportOption option,
							  boolean dryRun, TaskLogger logger) {
		Client client = newClient();
		try {
			Map<String, Optional<Long>> userIds = new HashMap<>();
			ImportResult result = new ImportResult();
			for (var giteaRepository : repositories.getImportRepositories()) {
				OneDev.getInstance(TransactionManager.class).run(() -> {
					try {
						String oneDevProjectPath;
						if (repositories.getParentOneDevProject() != null)
							oneDevProjectPath = repositories.getParentOneDevProject() + "/" + giteaRepository;
						else
							oneDevProjectPath = giteaRepository;

						logger.log("Importing from '" + giteaRepository + "' to '" + oneDevProjectPath + "'...");

						ProjectManager projectManager = OneDev.getInstance(ProjectManager.class);
						Project project = projectManager.setup(oneDevProjectPath);

						if (!project.isNew() && !SecurityUtils.canManageProject(project)) {
							throw new UnauthorizedException("Import target already exists. " +
									"You need to have project management privilege over it");
						}

						String apiEndpoint = getApiEndpoint("/repos/" + giteaRepository);
						JsonNode repoNode = JerseyUtils.get(client, apiEndpoint, logger);

						project.setDescription(repoNode.get("description").asText(null));
						project.setIssueManagement(repoNode.get("has_issues").asBoolean());

						if (project.isNew() || project.getDefaultBranch() == null) {
							logger.log("Cloning code...");

							URIBuilder builder = new URIBuilder(repoNode.get("clone_url").asText());
							builder.setUserInfo("git", getAccessToken());

							SecretMasker.push(text -> StringUtils.replace(text, getAccessToken(), "******"));
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

						boolean isPrivate = repoNode.get("private").asBoolean();
						if (!isPrivate && !option.getPublicRoles().isEmpty())
							OneDev.getInstance(BaseAuthorizationManager.class).syncRoles(project, option.getPublicRoles());

						if (option.getIssueImportOption() != null) {
							logger.log("Importing milestones...");
							apiEndpoint = getApiEndpoint("/repos/" + giteaRepository + "/milestones?state=all");
							for (JsonNode milestoneNode : list(client, apiEndpoint, logger)) {
								String milestoneName = milestoneNode.get("title").asText();
								Iteration iteration = project.getIteration(milestoneName);
								if (iteration == null) {
									iteration = new Iteration();
									iteration.setName(milestoneName);
									iteration.setDescription(milestoneNode.get("description").asText(null));
									iteration.setProject(project);
									String dueDateString = milestoneNode.get("due_on").asText(null);
									if (dueDateString != null)
										iteration.setDueDay(DateUtils.toLocalDate(ISODateTimeFormat.dateTimeNoMillis().parseDateTime(dueDateString).toDate(), ZoneId.systemDefault()).toEpochDay());
									if (milestoneNode.get("state").asText().equals("closed"))
										iteration.setClosed(true);

									project.getIterations().add(iteration);

									if (!dryRun)
										OneDev.getInstance(IterationManager.class).createOrUpdate(iteration);
								}
							}

							logger.log("Importing issues...");
							ImportResult currentResult = importIssues(giteaRepository,
									project, option.getIssueImportOption(), userIds, dryRun, logger);
							result.nonExistentLogins.addAll(currentResult.nonExistentLogins);
							result.nonExistentIterations.addAll(currentResult.nonExistentIterations);
							result.unmappedIssueLabels.addAll(currentResult.unmappedIssueLabels);
							result.issuesImported = result.issuesImported || currentResult.issuesImported;
						}
					} catch (URISyntaxException e) {
						throw new RuntimeException(e);
					}
				});
			}
			return new TaskResult(true, new HtmlMessgae(result.toHtml("Repositories imported successfully")));
		} finally {
			client.close();
		}
	}
	
	private GlobalIssueSetting getIssueSetting() {
		return OneDev.getInstance(SettingManager.class).getIssueSetting();
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
					.addParameter("limit", String.valueOf(PER_PAGE)).build();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		
		int page = 1;
		while (true) {
			try {
				URIBuilder builder = new URIBuilder(uri);
				builder.addParameter("page", String.valueOf(page));
				List<JsonNode> pageData = new ArrayList<>();
				for (JsonNode each: JerseyUtils.get(client, builder.build().toString(), logger)) 
					pageData.add(each);
				pageDataConsumer.consume(pageData);
				if (pageData.size() < PER_PAGE)
					break;
				page++;
			} catch (URISyntaxException|InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	private Client newClient() {
		Client client = ClientBuilder.newClient();
		client.property(ClientProperties.FOLLOW_REDIRECTS, true);
		client.register(OAuth2ClientSupport.feature(getAccessToken()));
		return client;
	}
	
	@Override
	public boolean isValid(ConstraintValidatorContext context) {
		Client client = ClientBuilder.newClient();
		client.register(OAuth2ClientSupport.feature(accessToken));
		try {
			String apiEndpoint = getApiEndpoint("/user");
			WebTarget target = client.target(apiEndpoint);
			Invocation.Builder builder =  target.request();
			try (Response response = builder.get()) {
				if (!response.getMediaType().toString().startsWith("application/json") 
						|| response.getStatus() == 404) {
					context.disableDefaultConstraintViolation();
					context.buildConstraintViolationWithTemplate("This does not seem like a Gitea api url")
							.addPropertyNode(PROP_API_URL).addConstraintViolation();
					return false;
				} else if (response.getStatus() == 401) {
					context.disableDefaultConstraintViolation();
					String errorMessage = "Authentication failed";
					context.buildConstraintViolationWithTemplate(errorMessage)
							.addPropertyNode(PROP_ACCESS_TOKEN).addConstraintViolation();
					return false;
				} else {
					String errorMessage = JerseyUtils.checkStatus(apiEndpoint, response);
					if (errorMessage != null) {
						context.disableDefaultConstraintViolation();
						context.buildConstraintViolationWithTemplate(errorMessage)
								.addPropertyNode(PROP_API_URL).addConstraintViolation();
						return false;
					}
				}
			}
		} catch (Exception e) {
			context.disableDefaultConstraintViolation();
			String errorMessage = "Error connecting api service";
			if (e.getMessage() != null)
				errorMessage += ": " + e.getMessage();
			context.buildConstraintViolationWithTemplate(errorMessage)
					.addPropertyNode(PROP_API_URL).addConstraintViolation();
			return false;
		} finally {
			client.close();
		}
		return true;
	}
	
}
