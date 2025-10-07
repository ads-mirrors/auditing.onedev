package io.onedev.server.model;

import io.onedev.server.OneDev;
import io.onedev.server.service.CodeCommentQueryPersonalizationService;
import io.onedev.server.model.support.NamedCodeCommentQuery;
import io.onedev.server.model.support.QueryPersonalization;
import io.onedev.server.util.watch.QuerySubscriptionSupport;
import io.onedev.server.util.watch.QueryWatchSupport;

import javax.persistence.*;
import java.util.ArrayList;

@Entity
@Table(
		indexes={@Index(columnList="o_project_id"), @Index(columnList="o_user_id")}, 
		uniqueConstraints={@UniqueConstraint(columnNames={"o_project_id", "o_user_id"})}
)
public class CodeCommentQueryPersonalization extends AbstractEntity implements QueryPersonalization<NamedCodeCommentQuery> {

	private static final long serialVersionUID = 1L;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(nullable=false)
	private Project project;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(nullable=false)
	private User user;

	@Lob
	@Column(nullable=false, length=65535)
	private ArrayList<NamedCodeCommentQuery> queries = new ArrayList<>();

	@Override
	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	@Override
	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	@Override
	public ArrayList<NamedCodeCommentQuery> getQueries() {
		return queries;
	}

	@Override
	public void setQueries(ArrayList<NamedCodeCommentQuery> queries) {
		this.queries = queries;
	}

	@Override
	public QueryWatchSupport<NamedCodeCommentQuery> getQueryWatchSupport() {
		return null;
	}

	@Override
	public QuerySubscriptionSupport<NamedCodeCommentQuery> getQuerySubscriptionSupport() {
		return null;
	}

	@Override
	public void onUpdated() {
		OneDev.getInstance(CodeCommentQueryPersonalizationService.class).createOrUpdate(this);
	}
	
}
