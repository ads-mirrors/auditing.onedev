package io.onedev.server.xodus;

import java.util.Collection;

import org.jspecify.annotations.Nullable;

import org.eclipse.jgit.lib.ObjectId;

import io.onedev.server.model.Project;
import io.onedev.server.model.PullRequest;

public interface PullRequestInfoService {

	Collection<Long> getPullRequestIds(Project project, ObjectId commitId);
	
	@Nullable
	ObjectId getComparisonBase(PullRequest request, ObjectId commitId1, ObjectId commitId2);
	
	void cacheComparisonBase(PullRequest request, ObjectId commitId1, ObjectId commitId2, ObjectId comparisonBase);
	
}
