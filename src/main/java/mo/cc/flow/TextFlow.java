package mo.cc.flow;

import com.alibaba.fastjson.annotation.JSONField;

public class TextFlow {
	public String description;
	public String text;
	public String url;
	
	@Override
	public String toString() {
		return "TextFlow [description=" + description 
				+ ", text=" + text + ", url=" + url + "]";
	}
	
	@JSONField(serialize=false)
	public boolean isValidated() {
		return text != null && !text.isEmpty();
	}
}