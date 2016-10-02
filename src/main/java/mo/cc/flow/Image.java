package mo.cc.flow;

import com.alibaba.fastjson.annotation.JSONField;

public class Image {
	public String src;
	public String alt;
	
	@Override
	public String toString() {
		return "Image [src=" + src + ", alt=" + alt + "]";
	}
	
	@JSONField(serialize=false)
	public boolean isValidated() {
		return src != null && !src.isEmpty();
	}
}