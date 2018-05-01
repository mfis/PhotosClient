package mfi.photos.client.model;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

public class Photo {

	public static final String DEST_PIC_DATATYPE = "jpg";

	public static final String DEST_VID_DATATYPE = "mp4";

	private boolean video;

	private String localName;

	private File localFile;

	private String localHash;

	private String remoteHash;

	private Dimension remoteSize;

	private Dimension remoteThumbnailSize;

	private long remoteFileSize;

	public Photo(File file) {
		this.localFile = file;
		this.localName = file.getName();
	}

	public String getLocalName() {
		return localName;
	}

	public String getRemoteName(String prefix) {
		String name = StringUtils.substringBeforeLast(localName, ".").replaceAll("[^a-zA-Z0-9]", "");
		name += "_" + Base64.encodeBase64URLSafeString(localName.getBytes(StandardCharsets.UTF_8));
		String remoteName = StringUtils.trimToEmpty(prefix) + name + "."
				+ (video && StringUtils.isBlank(prefix) ? DEST_VID_DATATYPE : DEST_PIC_DATATYPE);
		return remoteName;
	}

	public String getLocalHash() {
		return localHash;
	}

	public void setLocalHash(String localHash) {
		this.localHash = localHash;
	}

	public Dimension getRemoteSize() {
		return remoteSize;
	}

	public void setRemoteSize(Dimension remoteSize) {
		this.remoteSize = remoteSize;
	}

	public Dimension getRemoteThumbnailSize() {
		return remoteThumbnailSize;
	}

	public void setRemoteThumbnailSize(Dimension remoteThumbnailSize) {
		this.remoteThumbnailSize = remoteThumbnailSize;
	}

	public String getRemoteHash() {
		return remoteHash;
	}

	public void setRemoteHash(String remoteHash) {
		this.remoteHash = remoteHash;
	}

	public File getLocalFile() {
		return localFile;
	}

	public boolean isVideo() {
		return video;
	}

	public void setVideo(boolean video) {
		this.video = video;
	}

	public long getRemoteFileSize() {
		return remoteFileSize;
	}

	public void setRemoteFileSize(long remoteFileSize) {
		this.remoteFileSize = remoteFileSize;
	}

}