package mfi.photos.client.model;

public class Dimension {

	private int height;

	private int width;

	public Dimension(int w, int h) {
		height = h;
		width = w;
	}

	public void rotate() {
		int tempH = height;
		height = width;
		width = tempH;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}
}
