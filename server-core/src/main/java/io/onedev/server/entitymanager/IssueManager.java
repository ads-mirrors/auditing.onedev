package io.onedev.server.entitymanager;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;

import io.onedev.server.model.Issue;
import io.onedev.server.model.Iteration;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.persistence.dao.EntityManager;
import io.onedev.server.search.entity.EntityQuery;
import io.onedev.server.util.IssueTimes;
import io.onedev.server.util.IterationAndIssueState;
import io.onedev.server.util.ProjectIssueStateStat;
import io.onedev.server.util.ProjectScope;
import io.onedev.server.util.criteria.Criteria;
import io.onedev.server.web.component.issue.workflowreconcile.UndefinedFieldResolution;
import io.onedev.server.web.component.issue.workflowreconcile.UndefinedFieldValue;
import io.onedev.server.web.component.issue.workflowreconcile.UndefinedFieldValuesResolution;
import io.onedev.server.web.component.issue.workflowreconcile.UndefinedStateResolution;

public interface IssueManager extends EntityManager<Issue> {
	
    @Nullable
    Issue find(Project project, long number);

    @Nullable
    Issue find(String uuid);
    
	void open(Issue issue);
	
	void open(Issue issue, Collection<String> notifiedEmailAddresses);
	
	void togglePin(Issue issue);
	
	Long getNextNumber(Project numberScope);
	
	void resetNextNumber(Project numberScope);
	
	List<Issue> queryPinned(Project project);

	Predicate[] buildPredicates(@Nullable ProjectScope projectScope, @Nullable Criteria<Issue> issueCriteria,
								CriteriaQuery<?> query, CriteriaBuilder builder, From<Issue, Issue> issue);
	
	List<javax.persistence.criteria.Order> buildOrders(EntityQuery<Issue> query, CriteriaBuilder builder, 
													   From<Issue, Issue> issue, 
													   List<javax.persistence.criteria.Order> preferOrders);
	
	List<Issue> query(@Nullable ProjectScope projectScope, EntityQuery<Issue> issueQuery, 
			boolean loadExtraInfo, int firstResult, int maxResults);
	
	int count(@Nullable ProjectScope projectScope, @Nullable Criteria<Issue> issueCriteria);
	
	IssueTimes queryTimes(ProjectScope projectScope, @Nullable Criteria<Issue> issueCriteria);
	
	Collection<String> getUndefinedStates();
	
	void fixUndefinedStates(Map<String, UndefinedStateResolution> resolutions);
	
	Collection<String> getUndefinedFields();
	
	void fixUndefinedFields(Map<String, UndefinedFieldResolution> resolutions);
	
	Collection<UndefinedFieldValue> getUndefinedFieldValues();
	
	void fixUndefinedFieldValues(Map<String, UndefinedFieldValuesResolution> resolutions);
	
	void fixStateAndFieldOrdinals();
	
	@Override
	void delete(Issue issue);
	
	void move(Collection<Issue> issues, Project sourceProject, Project targetProject);

	void copy(Collection<Issue> issues, Project sourceProject, Project targetProject);
	
	void delete(Collection<Issue> issues, Project project);
	
	Collection<IterationAndIssueState> queryIterationAndIssueStates(Project project, Collection<Iteration> iterations);
	
	List<ProjectIssueStateStat> queryStateStats(Collection<Project> projects);
	
	Collection<Iteration> queryUsedIterations(Project project);

	void clearSchedules(Project project, Collection<Iteration> iterations);
	
	List<Issue> queryAfter(Long projectId, Long afterIssueId, int count);

	Collection<Long> parseFixedIssueIds(Project project, String commitMessage);
	
	List<Issue> loadIssues(List<Long> issueIds);

	Collection<Long> getProjectIds();

	List<Issue> query(User submitter, Date fromDate, Date toDate);
	
}
