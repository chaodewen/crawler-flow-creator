package mo.cc.flow;

import com.alibaba.fastjson.annotation.JSONField;

public class ListFlowElement {
	public String title;
	public String subtitle;
	public String url;
	public Image image;
	
	public ListFlowElement() {
		super();
		image = new Image();
	}
	
	@Override
	public String toString() {
		return "ListFlowElement [title=" + title + ", subtitle="
				+ subtitle + ", url=" + url + ", image=" + image + "]";
	}
	
	/**
	 * 只保证title, subtitle不空
	 */
	@JSONField(serialize=false)
	public boolean isValidated() {
		return title != null && !title.isEmpty()
				&& subtitle != null && !subtitle.isEmpty();
	}
}