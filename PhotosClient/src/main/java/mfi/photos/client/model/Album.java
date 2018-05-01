package mfi.photos.client.model;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;

public class Album {

	private String name;

	private String key;

	private String hashLocal;

	private String hashRemote;

	private LinkedList<Photo> photos;

	private List<String> photoRemoteNamesOutOfSync;

	private int doubletteCount;

	private boolean hasRemotePhotoData = false;

	private SyncStatus syncStatus;

	public Album(String name, String key) {
		photos = new LinkedList<>();
		photoRemoteNamesOutOfSync = new LinkedList<>();
		this.name = name;
		this.key = key;
		doubletteCount = 0;
	}

	public int countVideos() {
		int c = 0;
		for (Photo photo : photos) {
			if (photo.isVideo()) {
				c++;
			}
		}
		return c;
	}

	public long countFileSizeOutOfSync() {
		long sum = 0;
		for (Photo photo : photos) {
			if (photoRemoteNamesOutOfSync.contains(photo.getRemoteName(null))) {
				sum += photo.getLocalFile().length();
			}
		}
		return sum;
	}

	public Photo lookupPhotoByRemoteName(String remoteName) {
		for (Photo photo : photos) {
			if (photo.getRemoteName(null).equals(remoteName)) {
				return photo;
			}
		}
		return null;
	}

	public List<String> photoRemoteNamesAsList() {
		List<String> list = new LinkedList<>();
		for (Photo photo : photos) {
			list.add(photo.getRemoteName(null));
		}
		return list;
	}

	public String lookupAlbumHash() {
		String s = "";
		for (Photo photo : photos) {
			s += photo.getLocalHash() + "#";
		}
		byte[] hashBytes = DigestUtils.sha512(s);
		String hash = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(hashBytes);
		return hash;
	}

	public void increaseDoubletteCount() {
		doubletteCount++;
	}

	public String getName() {
		return name;
	}

	public String getKey() {
		return key;
	}

	public LinkedList<Photo> getPhotos() {
		return photos;
	}

	public void setPhotos(LinkedList<Photo> photos) {
		this.photos = photos;
	}

	public int getDoubletteCount() {
		return doubletteCount;
	}

	public String getHashLocal() {
		return hashLocal;
	}

	public void setHashLocal(String hashLocal) {
		this.hashLocal = hashLocal;
	}

	public String getHashRemote() {
		return hashRemote;
	}

	public void setHashRemote(String hashRemote) {
		this.hashRemote = hashRemote;
	}

	public List<String> getPhotoRemoteNamesOutOfSync() {
		return photoRemoteNamesOutOfSync;
	}

	public void setPhotoRemoteNamesOutOfSync(List<String> photoRemoteNamesOutOfSync) {
		this.photoRemoteNamesOutOfSync = photoRemoteNamesOutOfSync;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public boolean isHasRemotePhotoData() {
		return hasRemotePhotoData;
	}

	public void setHasRemotePhotoData(boolean hasRemotePhotoData) {
		this.hasRemotePhotoData = hasRemotePhotoData;
	}

	public SyncStatus getSyncStatus() {
		return syncStatus;
	}

	public void setSyncStatus(SyncStatus syncStatus) {
		this.syncStatus = syncStatus;
	}

}