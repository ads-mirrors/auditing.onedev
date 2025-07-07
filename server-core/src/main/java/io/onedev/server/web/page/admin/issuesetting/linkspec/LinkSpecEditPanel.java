package io.onedev.server.web.page.admin.issuesetting.linkspec;

import static io.onedev.server.web.translation.Translation._T;

import java.io.Serializable;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.GenericPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.cycle.RequestCycle;

import io.onedev.server.OneDev;
import io.onedev.server.data.migration.VersionedXmlDoc;
import io.onedev.server.entitymanager.AuditManager;
import io.onedev.server.entitymanager.LinkSpecManager;
import io.onedev.server.model.LinkSpec;
import io.onedev.server.util.Path;
import io.onedev.server.util.PathNode;
import io.onedev.server.web.ajaxlistener.ConfirmLeaveListener;
import io.onedev.server.web.editable.BeanContext;
import io.onedev.server.web.editable.BeanEditor;

abstract class LinkSpecEditPanel extends GenericPanel<LinkSpec> {

	private BeanEditor editor;
	
	private String oldName;
	
	private String oldOppositeName;

	private String oldAuditContent;
	
	public LinkSpecEditPanel(String id, IModel<LinkSpec> model) {
		super(id, model);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		Form<?> form = new Form<Void>("form") {

			@Override
			protected void onError() {
				super.onError();
				RequestCycle.get().find(AjaxRequestTarget.class).add(this);
			}
			
		};
		
		form.add(new AjaxLink<Void>("close") {

			@Override
			protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
				super.updateAjaxAttributes(attributes);
				attributes.getAjaxCallListeners().add(new ConfirmLeaveListener(LinkSpecEditPanel.this));
			}

			@Override
			public void onClick(AjaxRequestTarget target) {
				onCancel(target);
			}
			
		});
		
		editor = BeanContext.editModel("editor", new IModel<>() {

			@Override
			public void detach() {
			}

			@Override
			public Serializable getObject() {
				return getSpec();
			}

			@Override
			public void setObject(Serializable object) {
				oldName = getSpec().getName();
				if (getSpec().getOpposite() != null)
					oldOppositeName = getSpec().getOpposite().getName();
				editor.getDescriptor().copyProperties(object, getSpec());
			}

		});
		oldAuditContent = VersionedXmlDoc.fromBean(getSpec()).toXML();

		form.add(editor);
		
		form.add(new AjaxButton("save") {

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				super.onSubmit(target, form);

				if (getSpec().getOpposite() != null && getSpec().getName().equals(getSpec().getOpposite().getName())) {
					String errorMessage = _T("Name and name on the other side should be different");
					editor.error(new Path(new PathNode.Named("name")), errorMessage);
					editor.error(new Path(new PathNode.Named("opposite"), new PathNode.Named("name")), errorMessage);
					target.add(form);
				} else {
					var linkSpecManager = OneDev.getInstance(LinkSpecManager.class);
					var auditManager = OneDev.getInstance(AuditManager.class);
					LinkSpec specWithSameName = linkSpecManager.find(getSpec().getName());
					if (getSpec().isNew() && specWithSameName != null 
							|| !getSpec().isNew() && specWithSameName != null && !specWithSameName.equals(getSpec())) {
						editor.error(new Path(new PathNode.Named("name")), _T("Name already used by another link"));
						target.add(form);
					} else if (getSpec().getOpposite() != null) {
						specWithSameName = linkSpecManager.find(getSpec().getOpposite().getName());
						if (getSpec().isNew() && specWithSameName != null 
								|| !getSpec().isNew() && specWithSameName != null && !specWithSameName.equals(getSpec())) {
							String errorMessage = _T("Name already used by another link");
							editor.error(new Path(new PathNode.Named("opposite"), new PathNode.Named("name")), errorMessage);
							target.add(form);
						} else {
							var newAuditContent = VersionedXmlDoc.fromBean(getSpec()).toXML();
							if (getSpec().isNew()) {
								getSpec().setOrder(linkSpecManager.query().stream().mapToInt(it -> it.getOrder()).max().orElse(0) + 1);
								linkSpecManager.create(getSpec());
								auditManager.audit(null, "added issue link spec \"" + getSpec().getDisplayName() + "\"", null, newAuditContent);
							} else {
								linkSpecManager.update(getSpec(), oldName, oldOppositeName);
								auditManager.audit(null, "changed issue link spec \"" + getSpec().getDisplayName() + "\"", oldAuditContent, newAuditContent);
							}
							oldAuditContent = newAuditContent;
							onSave(target);
						}
					} else {
						var newAuditContent = VersionedXmlDoc.fromBean(getSpec()).toXML();
						if (getSpec().isNew()) {
							getSpec().setOrder(linkSpecManager.query().stream().mapToInt(it -> it.getOrder()).max().orElse(0) + 1);
							linkSpecManager.create(getSpec());
							auditManager.audit(null, "added issue link spec \"" + getSpec().getDisplayName() + "\"", null, newAuditContent);
						} else {
							linkSpecManager.update(getSpec(), oldName, oldOppositeName);
							auditManager.audit(null, "changed issue link spec \"" + getSpec().getDisplayName() + "\"", oldAuditContent, newAuditContent);
						}
						oldAuditContent = newAuditContent;
						onSave(target);
					}
				}
			}
			
		});
		
		form.add(new AjaxLink<Void>("cancel") {

			@Override
			protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
				super.updateAjaxAttributes(attributes);
				attributes.getAjaxCallListeners().add(new ConfirmLeaveListener(LinkSpecEditPanel.this));
			}

			@Override
			public void onClick(AjaxRequestTarget target) {
				onCancel(target);
			}
			
		});
		form.setOutputMarkupId(true);
		
		add(form);
	}
	
	private LinkSpec getSpec() {
		return getModelObject();
	}

	protected abstract void onSave(AjaxRequestTarget target);
	
	protected abstract void onCancel(AjaxRequestTarget target);

}
