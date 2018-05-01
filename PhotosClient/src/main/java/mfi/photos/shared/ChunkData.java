package mfi.photos.shared;

public class ChunkData {

	public String filename;
	public String dir;
	public int read;
	public byte[] bytesIns;
	public boolean append;

	public ChunkData(String filename, String dir, int read, byte[] bytesIns, boolean append) {
		this.filename = filename;
		this.dir = dir;
		this.read = read;
		this.bytesIns = bytesIns;
		this.append = append;
	}
}