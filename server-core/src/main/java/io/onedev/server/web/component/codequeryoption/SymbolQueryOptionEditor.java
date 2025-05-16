package io.onedev.server.web.component.codequeryoption;

import io.onedev.commons.utils.StringUtils;
import io.onedev.server.search.code.query.SymbolQuery;
import io.onedev.server.search.code.query.SymbolQueryOption;
import io.onedev.server.search.code.query.TooGeneralQueryException;

import static io.onedev.server.web.translation.Translation._T;

import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.feedback.FencedFeedbackPanel;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.validation.IValidationError;

public class SymbolQueryOptionEditor extends FormComponentPanel<SymbolQueryOption> {

	private TextField<String> term;
	
	private CheckBox caseSensitive;
	
	private TextField<String> fileNames;

	public SymbolQueryOptionEditor(String id, IModel<SymbolQueryOption> model) {
		super(id, model);
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		var option = getModelObject();
		WebMarkupContainer termContainer = new WebMarkupContainer("term");
		add(termContainer);
		term = new TextField<>("term", Model.of(option.getTerm()));
		term.setRequired(true).setLabel(Model.of(_T("Symbol name")));
		term.add(validatable -> {
			if (StringUtils.isBlank(validatable.getValue())) {
				validatable.error(messageSource -> _T("This field is required"));
			} else {
				try {
					new SymbolQuery.Builder(validatable.getValue())
							.count(1)
							.build()
							.asLuceneQuery();
				} catch (TooGeneralQueryException e) {
					validatable.error((IValidationError) messageSource -> _T("Search is too general"));
				}
			}
		});
		termContainer.add(term);
		termContainer.add(new FencedFeedbackPanel("feedback", term));
		termContainer.add(AttributeAppender.append("class", new LoadableDetachableModel<String>() {
			
			@Override
			protected String load() {
				if (term.hasErrorMessage())
					return " is-invalid";
				else
					return "";
			}

		}));

		add(caseSensitive = new CheckBox("caseSensitive", Model.of(option.isCaseSensitive())));

		add(fileNames = new TextField<String>("fileNames", Model.of(option.getFileNames())));
	}

	@Override
	public void convertInput() {
		setConvertedInput(new SymbolQueryOption(term.getConvertedInput(), caseSensitive.getConvertedInput(), fileNames.getConvertedInput()));
	}
	
}
