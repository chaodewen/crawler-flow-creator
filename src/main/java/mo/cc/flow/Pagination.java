package mo.cc.flow;

public class Pagination {
	public int page;
	public int pages;
	public boolean has_next;
	
	@Override
	public String toString() {
		return "Pagination [page=" + page + ", pages="
				+ pages + ", has_next=" + has_next + "]";
	}
}