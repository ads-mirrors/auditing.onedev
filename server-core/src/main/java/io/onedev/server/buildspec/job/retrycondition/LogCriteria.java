package io.onedev.server.buildspec.job.retrycondition;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;

import io.onedev.server.OneDev;
import io.onedev.server.job.log.LogService;
import io.onedev.server.model.Build;
import io.onedev.server.util.ProjectScope;
import io.onedev.server.util.criteria.Criteria;

public class LogCriteria extends Criteria<RetryContext> {

	private static final long serialVersionUID = 1L;
	
	private final String value;
	
	public LogCriteria(String value) {
		this.value = value;
	}
	
	@Override
	public Predicate getPredicate(@Nullable ProjectScope projectScope, CriteriaQuery<?> query, From<RetryContext, RetryContext> from, CriteriaBuilder builder) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean matches(RetryContext context) {
		Pattern pattern = Pattern.compile(value);
		return context.getErrorMessage() != null && pattern.matcher(context.getErrorMessage()).find() 
				|| OneDev.getInstance(LogService.class).matches(context.getBuild(), pattern);
	}

	@Override
	public String toStringWithoutParens() {
		return quote(Build.NAME_LOG) + " " 
				+ RetryCondition.getRuleName(RetryConditionLexer.Contains) + " "
				+ quote(value);
	}
	
}
