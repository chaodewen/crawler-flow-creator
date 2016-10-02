package mo.cc.flow;

import java.util.List;

import com.alibaba.fastjson.annotation.JSONField;

public class ImageFlow {
	public String description;
	public Pagination pagination;
	public String text;
	public String url;
	public List<Image> images;
	
	@Override
	public String toString() {
		return "ImageFlow [description=" + description + ", pagination="
				+ pagination + ", text=" + text + ", url="
				+ url + ", images=" + images + "]";
	}
	
	@JSONField(serialize=false)
	public boolean isValidated() {
		if(pagination == null || text == null || text.isEmpty()) {
			return false;
		}
		else {
			for(Image image : images) {
				if(!image.isValidated()) {
					return false;
				}
			}
			return true;
		}
	}
}