package io.onedev.server.model.support.issue.transitionspec;

import static io.onedev.server.web.translation.Translation._T;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import io.onedev.commons.codeassist.InputSuggestion;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.IssueQuery;
import io.onedev.server.annotation.Patterns;
import io.onedev.server.model.Project;
import io.onedev.server.search.entity.issue.IssueQueryLexer;
import io.onedev.server.util.patternset.PatternSet;
import io.onedev.server.util.usage.Usage;
import io.onedev.server.web.util.SuggestionUtils;

@Editable(order=400, name="Build is successful")
public class BuildSuccessfulSpec extends AutoSpec {

	private static final long serialVersionUID = 1L;
	
	private String jobNames;
	
	private String branches;
	
	public BuildSuccessfulSpec() {
		setIssueQuery(io.onedev.server.search.entity.issue.IssueQuery
				.getRuleName(IssueQueryLexer.FixedInCurrentBuild));		
	}
	
	@Editable(order=100, name="Applicable Jobs", placeholder="Any job", description="Optionally specify space-separated jobs "
			+ "applicable for this transition. Use '*' or '?' for wildcard match. Prefix with '-' to exclude. "
			+ "Leave empty to match all")
	@Patterns(suggester = "suggestJobs")
	public String getJobNames() {
		return jobNames;
	}

	public void setJobNames(String jobNames) {
		this.jobNames = jobNames;
	}

	@Editable(order=200, name="Applicable Branches", placeholder="Any branch", description="Optionally specify space-separated branches "
			+ "applicable for this transition. Use '**', '*' or '?' for <a href='https://docs.onedev.io/appendix/path-wildcard' target='_blank'>path wildcard match</a>. "
			+ "Prefix with '-' to exclude. Leave empty to match all")
	@Patterns(suggester = "suggestBranches", path=true)
	public String getBranches() {
		return branches;
	}

	public void setBranches(String branches) {
		this.branches = branches;
	}
	
	@SuppressWarnings("unused")
	private static List<InputSuggestion> suggestBranches(String matchWith) {
		Project project = Project.get();
		if (project != null)
			return SuggestionUtils.suggestBranches(project, matchWith);
		else
			return new ArrayList<>();
	}
	
	@SuppressWarnings("unused")
	private static List<InputSuggestion> suggestJobs(String matchWith) {
		Project project = Project.get();
		if (project != null)
			return SuggestionUtils.suggest(project.getJobNames(), matchWith);
		else
			return new ArrayList<>();
	}
	
	@Editable(order=9900, name="Applicable Issues", placeholder="All", description="Optionally specify issues "
			+ "applicable for this transition. Leave empty for all issues")
	@IssueQuery(withOrder = false, withCurrentBuildCriteria = true)
	@Override
	public String getIssueQuery() {
		return super.getIssueQuery();
	}

	public void setIssueQuery(String issueQuery) {
		super.setIssueQuery(issueQuery);
	}

	@Override
	public Usage onDeleteBranch(String branchName) {
		Usage usage = super.onDeleteBranch(branchName);
		PatternSet patternSet = PatternSet.parse(getBranches());
		if (patternSet.getIncludes().contains(branchName) || patternSet.getExcludes().contains(branchName))
			usage.add("applicable branches");
		return usage;
	}
	
	@Override
	public String getTriggerDescription() {
		if (jobNames != null) {
			if (branches != null)
				return MessageFormat.format(_T("build is successful for jobs \"{0}\" on branches \"{1}\""), jobNames, branches);
			else
				return MessageFormat.format(_T("build is successful for jobs \"{0}\" on any branch"), jobNames);
		} else {
			if (branches != null)
				return MessageFormat.format(_T("build is successful for any job on branches \"{0}\""), branches);
			else
				return _T("build is successful for any job and branch");
		}
	}
	
}
