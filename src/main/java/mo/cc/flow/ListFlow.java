package mo.cc.flow;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.annotation.JSONField;

public class ListFlow {
	public String description;
	public Pagination pagination;
	public List<ListFlowElement> list;
	
	public ListFlow() {
		super();
		pagination = new Pagination();
		list = new ArrayList<ListFlowElement>();
	}
	
	@Override
	public String toString() {
		return "ListFlow [description=" + description 
				+ ", pagination=" + pagination + ", list=" + list + "]";
	}
	
	@JSONField(serialize=false)
	public boolean isValidated() {
		if(pagination == null) {
			return false;
		}
		else {
			for(ListFlowElement item : list) {
				if(!item.isValidated()) {
					return false;
				}
			}
			return true;
		}
	}
}