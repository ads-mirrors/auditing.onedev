package io.onedev.server.git.command;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.onedev.commons.utils.command.Commandline;
import org.apache.commons.lang3.time.DateFormatUtils;

import io.onedev.server.git.command.RevListCommand.Order;

import org.jspecify.annotations.Nullable;

public class RevListOptions implements Serializable {

	private static final long serialVersionUID = 1L;

    private List<String> revisions = new ArrayList<>();
    
    private List<String> paths = new ArrayList<>();
    
    private String after;
    
    private String before;
    
    private int count;
    
    private int skip;
    
    private Order order;
    
    private boolean firstParent;
	
    private boolean ignoreCase;
    
    private List<String> messages = new ArrayList<>();
    
    private List<String> authors = new ArrayList<>();
    
    private List<String> committers = new ArrayList<>();
	
	public List<String> revisions() {
		return revisions;
	}

	public RevListOptions revisions(List<String> revisions) {
		this.revisions = revisions;
		return this;
	}

	public List<String> paths() {
		return paths;
	}

	public RevListOptions paths(List<String> paths) {
		this.paths = paths;
		return this;
	}

	@Nullable
	public Order order() {
		return order;
	}
	
	public RevListOptions order(@Nullable Order order) {
		this.order = order;
		return this;
	}
	
	public boolean firstParent() {
		return firstParent;
	}
	
	public RevListOptions firstParent(boolean firstParent) {
		this.firstParent = firstParent;
		return this;
	}

	public String after() {
		return after;
	}

	public RevListOptions after(String after) {
		this.after = after;
		return this;
	}

	public RevListOptions after(Date after) {
		this.after = DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.format(after);
		return this;
	}

	public String before() {
		return before;
	}

	public RevListOptions before(String before) {
		this.before = before;
		return this;
	}
	
	public RevListOptions before(Date before) {
		this.before = DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.format(before);		
		return this;
	}
	
	public int count() {
		return count;
	}

	public RevListOptions count(int count) {
		this.count = count;
		return this;
	}

	public int skip() {
		return skip;
	}

	public RevListOptions skip(int skip) {
		this.skip = skip;
		return this;
	}
	
	public boolean ignoreCase() {
		return ignoreCase;
	}
	
	public RevListOptions ignoreCase(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
		return this;
	}
	
	public List<String> messages() {
		return messages;
	}

	public RevListOptions messages(List<String> messages) {
		this.messages = messages;
		return this;
	}

	public List<String> authors() {
		return authors;
	}
	
	public RevListOptions authors(List<String> authors) {
		this.authors = authors;
		return this;
	}
	
	public List<String> committers() {
		return committers;
	}
	
	public RevListOptions commmitters(List<String> committers) {
		this.committers = committers;
		return this;
	}
	
	public void configure(Commandline git) {
		boolean hasRevisions = false;
		if (!revisions().isEmpty()) {
			for (String revision: revisions()) {
				git.addArgs(revision);
				if (!revision.startsWith("^"))
					hasRevisions = true;
			}
		}
		if (!hasRevisions)
			git.addArgs("--branches");

		if (before() != null)
			git.addArgs("--before", before());

		if (after() != null)
			git.addArgs("--after", after());

		if (count() != 0)
			git.addArgs("-" + count());
		if (skip() != 0)
			git.addArgs("--skip=" + skip());

		if (order() == Order.DATE)
			git.addArgs("--date-order");
		else if (order() == Order.AUTHOR_DATE)
			git.addArgs("--author-date-order");
		else if (order() == Order.TOPO)
			git.addArgs("--topo-order");

		if (firstParent())
			git.addArgs("--first-parent");

		for (String author: authors())
			git.addArgs("--author=" + author);

		for (String committer: committers())
			git.addArgs("--committer=" + committer);

		for (String message: messages())
			git.addArgs("--grep=" + message);

		if (ignoreCase())
			git.addArgs("-i");

		git.addArgs("--");

		for (String path: paths())
			git.addArgs(path);
	}
}