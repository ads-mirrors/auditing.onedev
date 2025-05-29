package io.onedev.server.xodus;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;

import io.onedev.server.git.GitContribution;
import io.onedev.server.git.GitContributor;
import io.onedev.server.model.User;
import io.onedev.server.util.NameAndEmail;
import io.onedev.server.util.facade.EmailAddressFacade;

public interface CommitInfoManager {
	
	int getFileCount(Long projectId);
	
	List<String> getFiles(Long projectId);
	
	int getCommitCount(Long projectId);
	
	void export(Long projectId, File targetDir);
	
	List<NameAndEmail> getUsers(Long projectId);
	
	/**
	 * Given an ancestor commit, get all its descendant commits including the ancestor commit itself. 
	 * The result might be incomplete if some commits have not be cached yet
	 *  
	 * @param projectId
	 * 			project to get descendant commits
	 * @param ancestors
	 * 			for which commits to get descendants
	 * @return
	 * 			descendant commits
	 */
	Collection<ObjectId> getDescendants(Long projectId, Collection<ObjectId> ancestors);
	
	void cloneInfo(Long sourceProjectId, Long targetProjectId);
	
	Collection<String> getHistoryPaths(Long projectId, String path);
	
	/**
	 * Get overall contributions
	 * 
	 * @param projectId
	 * 			project to get daily commits for
	 * @return
	 * 			map of day to contribution
	 */
	Map<Integer, GitContribution> getOverallContributions(Long projectId);
	
	/**
	 * Get list of top contributors
	 * 
	 * @param projectId
	 * 			project to get top contributors for
	 * @param top
	 * 			number of top contributors to get
	 * @param type
	 * 			type of contribution to order by
	 * @param fromDay
	 * 			from day
	 * @param toDay
	 * 			to day
	 * @return
	 * 			list of top user contributors, reversely ordered by number of contributions 
	 */
	List<GitContributor> getTopContributors(Long projectId, int top, 
			GitContribution.Type type, int fromDay, int toDay);

	Map<Long, Map<ObjectId, Long>> getUserCommits(User user, Date fromDate, Date toDate);

	/**
	 * Get source code line statistics over time
	 * 
	 * @param projectId
	 * 			project to get line stats for
	 * @return
	 * 			line statistics data
	 */
	Map<Integer, Map<String, Integer>> getLineIncrements(Long projectId);

	Collection<ObjectId> getFixCommits(Long projectId, Long issueId, boolean headOnly);
	
	List<Long> sortUsersByContribution(Map<Long, Collection<EmailAddressFacade>> userEmails, 
			Long projectId, Collection<String> files);
	
}