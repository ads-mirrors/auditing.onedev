package io.onedev.server.util;

import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.onedev.server.git.GitUtils;
import io.onedev.server.git.exception.ObjectNotFoundException;
import io.onedev.server.model.Project;

public class RevisionAndPath implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String revision;
	
	private final String path;
	
	public RevisionAndPath(@Nullable String revision, @Nullable String path) {
		this.revision = revision;
		this.path = path;
	}
	
	public static RevisionAndPath parse(Project project, List<String> segments) {
		String revision = null;
		String path = null;
		
		StringBuilder revisionBuilder = new StringBuilder();
		for (int i=0; i<segments.size(); i++) {
			if (i != 0)
				revisionBuilder.append("/");
			revisionBuilder.append(segments.get(i));
			if (project.getObjectId(revisionBuilder.toString(), false) != null) {
				revision = revisionBuilder.toString();
				StringBuilder pathBuilder = new StringBuilder();
				for (int j=i+1; j<segments.size(); j++) {
					if (j != i+1)
						pathBuilder.append("/");
					pathBuilder.append(segments.get(j));
				}
				if (pathBuilder.length() != 0) {
					path = pathBuilder.toString();
				}
				return new RevisionAndPath(revision, path);
			}
		}
		if (revisionBuilder.length() != 0) {
			throw new ObjectNotFoundException("Revision/path not found: " + revisionBuilder.toString());
		} else {
			revision = project.getDefaultBranch();
			if (revision != null && project.getTagRef(revision) != null)
				return new RevisionAndPath(GitUtils.branch2ref(revision), path);
			else
				return new RevisionAndPath(revision, path);				
		}
	}

	@Nullable
	public String getRevision() {
		return revision;
	}

	@Nullable
	public String getPath() {
		return path;
	}
	
}
