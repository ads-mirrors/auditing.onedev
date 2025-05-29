package io.onedev.server.entitymanager.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;

import org.joda.time.DateTime;

import com.google.common.base.Preconditions;

import io.onedev.commons.utils.ExplicitException;
import io.onedev.server.entitymanager.PullRequestChangeManager;
import io.onedev.server.entitymanager.PullRequestDescriptionRevisionManager;
import io.onedev.server.entityreference.ReferenceChangeManager;
import io.onedev.server.event.ListenerRegistry;
import io.onedev.server.event.project.pullrequest.PullRequestChanged;
import io.onedev.server.model.PullRequest;
import io.onedev.server.model.PullRequestChange;
import io.onedev.server.model.PullRequestComment;
import io.onedev.server.model.PullRequestDescriptionRevision;
import io.onedev.server.model.User;
import io.onedev.server.model.support.pullrequest.AutoMerge;
import io.onedev.server.model.support.pullrequest.MergeStrategy;
import io.onedev.server.model.support.pullrequest.changedata.PullRequestAutoMergeChangeData;
import io.onedev.server.model.support.pullrequest.changedata.PullRequestDescriptionChangeData;
import io.onedev.server.model.support.pullrequest.changedata.PullRequestMergeStrategyChangeData;
import io.onedev.server.model.support.pullrequest.changedata.PullRequestTargetBranchChangeData;
import io.onedev.server.model.support.pullrequest.changedata.PullRequestTitleChangeData;
import io.onedev.server.persistence.annotation.Transactional;
import io.onedev.server.persistence.dao.BaseEntityManager;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.security.SecurityUtils;

@Singleton
public class DefaultPullRequestChangeManager extends BaseEntityManager<PullRequestChange> 
		implements PullRequestChangeManager {
	
	private final PullRequestDescriptionRevisionManager descriptionRevisionManager;
	
	private final ListenerRegistry listenerRegistry;
	
	private final ReferenceChangeManager entityReferenceManager;
	
	@Inject
	public DefaultPullRequestChangeManager(Dao dao, ListenerRegistry listenerRegistry, 
										   ReferenceChangeManager entityReferenceManager, 
										   PullRequestDescriptionRevisionManager descriptionRevisionManager) {
		super(dao);
		this.listenerRegistry = listenerRegistry;
		this.entityReferenceManager = entityReferenceManager;
		this.descriptionRevisionManager = descriptionRevisionManager;
	}

	@Transactional
	@Override
	public void create(PullRequestChange change, @Nullable String note) {
		Preconditions.checkState(change.isNew());
		dao.persist(change);
		change.getRequest().getChanges().add(change);
		if (note != null && change.getUser() != null) {
			PullRequestComment comment = new PullRequestComment();
			comment.setContent(note);
			comment.setUser(change.getUser());
			comment.setRequest(change.getRequest());
			comment.setDate(new DateTime(change.getDate()).minusSeconds(1).toDate());
			dao.persist(comment);
			comment.getRequest().setCommentCount(comment.getRequest().getCommentCount()+1);
		}
		
		listenerRegistry.post(new PullRequestChanged(change, note));
	}
	
	@Transactional
	@Override
	public void changeMergeStrategy(PullRequest request, MergeStrategy mergeStrategy) {
		PullRequestChange change = new PullRequestChange();
		change.setDate(new Date());
		change.setRequest(request);
		change.setData(new PullRequestMergeStrategyChangeData(request.getMergeStrategy(), mergeStrategy));
		change.setUser(SecurityUtils.getUser());
		create(change, null);
		request.getAutoMerge().setEnabled(false);
		request.setMergeStrategy(mergeStrategy);
	}

	@Transactional
	@Override
	public void changeAutoMerge(PullRequest request, AutoMerge autoMerge) {
		if (request.getAutoMerge().isEnabled() && !autoMerge.isEnabled() 
				|| !request.getAutoMerge().isEnabled() && autoMerge.isEnabled()) {
			PullRequestChange change = new PullRequestChange();
			change.setDate(new Date());
			change.setRequest(request);
			change.setData(new PullRequestAutoMergeChangeData(autoMerge.isEnabled()));
			change.setUser(SecurityUtils.getUser());
			create(change, null);
		}
		request.setAutoMerge(autoMerge);
	}
	
	@Transactional
	@Override
	public void changeTitle(PullRequest request, String title) {
		String prevTitle = request.getTitle();
		if (!title.equals(prevTitle)) {
			request.setTitle(title);
			
			PullRequestChange change = new PullRequestChange();
			change.setDate(new Date());
			change.setRequest(request);
			change.setData(new PullRequestTitleChangeData(prevTitle, title));
			change.setUser(SecurityUtils.getUser());
			create(change, null);
			
			dao.persist(request);
		}
	}

	@Transactional
	@Override
	public void changeDescription(PullRequest request, @Nullable String description) {
		String prevDescription = request.getDescription();
		if (!Objects.equals(description, prevDescription)) {
			if (description != null && description.length() > PullRequest.MAX_DESCRIPTION_LEN)
				throw new ExplicitException("Description too long");
			request.setDescription(description);
			request.setDescriptionRevisionCount(request.getDescriptionRevisionCount() + 1);
			entityReferenceManager.addReferenceChange(SecurityUtils.getUser(), request, description);

			var user = SecurityUtils.getUser();

			PullRequestChange change = new PullRequestChange();
			change.setRequest(request);
			change.setUser(user);
			change.setData(new PullRequestDescriptionChangeData(prevDescription, request.getDescription()));
			create(change, null);
			dao.persist(request);

			var revision = new PullRequestDescriptionRevision();
			revision.setRequest(request);
			revision.setUser(user);
			revision.setOldContent(prevDescription);
			revision.setNewContent(description);
			descriptionRevisionManager.create(revision);
		}
	}
	
	@Transactional
	@Override
	public void changeTargetBranch(PullRequest request, String targetBranch) {
		String prevTargetBranch = request.getTargetBranch();
		if (!targetBranch.equals(prevTargetBranch)) {
			request.setTargetBranch(targetBranch);
			request.getAutoMerge().setEnabled(false);
			
			PullRequestChange change = new PullRequestChange();
			change.setDate(new Date());
			change.setRequest(request);
			change.setData(new PullRequestTargetBranchChangeData(prevTargetBranch, targetBranch));
			change.setUser(SecurityUtils.getUser());
			create(change, null);
			
			dao.persist(request);
		}
	}

	@Override
	public List<PullRequestChange> query(User submitter, Date fromDate, Date toDate) {
		CriteriaBuilder builder = getSession().getCriteriaBuilder();
		CriteriaQuery<PullRequestChange> query = builder.createQuery(PullRequestChange.class);
		From<PullRequestChange, PullRequestChange> root = query.from(PullRequestChange.class);
		
		List<Predicate> predicates = new ArrayList<>();

		predicates.add(builder.equal(root.get(PullRequestChange.PROP_USER), submitter));
		predicates.add(builder.greaterThanOrEqualTo(root.get(PullRequestChange.PROP_DATE), fromDate));
		predicates.add(builder.lessThanOrEqualTo(root.get(PullRequestChange.PROP_DATE), toDate));
			
		query.where(predicates.toArray(new Predicate[0]));
		
		return getSession().createQuery(query).getResultList();
	}

}
