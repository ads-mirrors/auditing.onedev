package io.onedev.server.security.permission;

import io.onedev.server.util.facade.UserFacade;
import org.apache.shiro.authz.Permission;

import org.jspecify.annotations.Nullable;
import java.util.Collection;

public class EditIssueField implements BasePermission {

	private final Collection<String> fields;
	
	public EditIssueField(@Nullable Collection<String> fields) {
		this.fields = fields;
	}
	
	@Override
	public boolean implies(Permission p) {
		if (p instanceof EditIssueField) {
			EditIssueField editIssueField = (EditIssueField) p;
			if (fields == null)
				return true;
			else if (editIssueField.fields == null)
				return false;
			else
				return fields.containsAll(editIssueField.fields);
		} else {
			return new AccessProject().implies(p);
		}
	}

	@Override
	public boolean isApplicable(@Nullable UserFacade user) {
		return user != null;
	}
	
}
