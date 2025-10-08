package io.onedev.server.search.entity.pullrequest;

import org.jspecify.annotations.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;

import io.onedev.commons.utils.match.WildcardUtils;
import io.onedev.server.model.PullRequest;
import io.onedev.server.util.ProjectScope;
import io.onedev.server.util.criteria.Criteria;

public class TitleCriteria extends Criteria<PullRequest> {

	private static final long serialVersionUID = 1L;

	private final String value;
	
	public TitleCriteria(String value) {
		this.value = value;
	}

	@Override
	public Predicate getPredicate(@Nullable ProjectScope projectScope, CriteriaQuery<?> query, From<PullRequest, PullRequest> from, CriteriaBuilder builder) {
		Expression<String> attribute = from.get(PullRequest.PROP_TITLE);
		return builder.like(builder.lower(attribute), "%" + value.toLowerCase().replace('*', '%') + "%");
	}

	@Override
	public boolean matches(PullRequest request) {
		String title = request.getTitle();
		return WildcardUtils.matchString("*" + value.toLowerCase() + "*", title.toLowerCase());
	}

	@Override
	public String toStringWithoutParens() {
		return quote(PullRequest.NAME_TITLE) + " " 
				+ PullRequestQuery.getRuleName(PullRequestQueryLexer.Contains) + " " 
				+ quote(value);
	}

}
