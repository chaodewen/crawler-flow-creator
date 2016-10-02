package mo.cc.flow;

import com.alibaba.fastjson.annotation.JSONField;

public class ErrorFlow {
	public String description;
	public String text;
	public String url;
	public String error;
	
	@Override
	public String toString() {
		return "ErrorFlow [description=" + description 
				+ ", text=" + text + ", url=" + url 
				+ ", error=" + error + "]";
	}

	@JSONField(serialize=false)
	public boolean isValidated() {
		return text != null && !text.isEmpty();
	}
}