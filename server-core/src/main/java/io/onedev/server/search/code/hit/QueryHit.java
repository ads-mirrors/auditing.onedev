package io.onedev.server.search.code.hit;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.image.Image;

import io.onedev.commons.utils.PlanarRange;

public abstract class QueryHit implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private final String blobPath;
	
	private final PlanarRange hitPos;
	
	public QueryHit(String blobPath, @Nullable PlanarRange hitPos) {
		this.blobPath = blobPath;
		this.hitPos = hitPos;
	}

	public String getBlobPath() {
		return blobPath;
	}
	
	@Nullable
	public PlanarRange getHitPos() {
		return hitPos;
	}

	public abstract Component render(String componentId);
	
	@Nullable
	public abstract String getNamespace();
	
	public abstract Image renderIcon(String componentId);
	
}
