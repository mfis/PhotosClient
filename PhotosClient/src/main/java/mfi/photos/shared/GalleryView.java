package mfi.photos.shared;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class GalleryView {

	public GalleryView(String key, String galleryname, int size, String[] users, String baseURL, String galleryhash) {
		this.key = key;
		this.galleryname = galleryname;
		this.users = users;
		this.pictures = new Picture[size];
		this.baseURL = baseURL;
		this.galleryhash = galleryhash;
	}

	public void addItem(String name, int tnh, int tnw, int h, int w, String hash, long fileSize) {
		Picture newPicture = new Picture();
		newPicture.name = name;
		newPicture.tnh = tnh;
		newPicture.tnw = tnw;
		newPicture.h = h;
		newPicture.w = w;
		newPicture.hash = hash;
		newPicture.fileSize = fileSize;
		for (int i = 0; i < this.pictures.length; i++) {
			if (this.pictures[i] == null) {
				this.pictures[i] = newPicture;
				break;
			}
		}
	}

	public void compressItems() {
		List<Picture> picList = new LinkedList<Picture>();
		for (Picture picture : pictures) {
			if (picture != null) {
				picList.add(picture);
			}
		}
		this.pictures = new Picture[picList.size()];
		this.pictures = picList.toArray(this.pictures);
	}

	public void truncateHashes() {
		for (Picture picture : pictures) {
			picture.hash = null;
		}
	}

	private String key;
	private String galleryname;
	private String galleryDisplayName;
	private String galleryDisplayIdentifier;
	private String galleryDisplayNormDate;
	private String baseURL;
	private String galleryhash;

	private Picture pictures[];
	private String[] users;

	public class Picture {
		private String name;
		private int tnh;
		private int tnw;
		private int h;
		private int w;
		private String hash;
		private long fileSize;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getHash() {
			return hash;
		}

		public void setHash(String hash) {
			this.hash = hash;
		}

		public int getTnh() {
			return tnh;
		}

		public void setTnh(int tnh) {
			this.tnh = tnh;
		}

		public int getTnw() {
			return tnw;
		}

		public void setTnw(int tnw) {
			this.tnw = tnw;
		}

		public int getH() {
			return h;
		}

		public void setH(int h) {
			this.h = h;
		}

		public int getW() {
			return w;
		}

		public void setW(int w) {
			this.w = w;
		}

		public long getFileSize() {
			return fileSize;
		}

		public void setFileSize(long fileSize) {
			this.fileSize = fileSize;
		}

	}

	public String getGalleryname() {
		return galleryname;
	}

	public String getKey() {
		return key;
	}

	public List<String> getUsersAsList() {
		if (users == null) {
			return new LinkedList<String>();
		} else {
			return Arrays.asList(users);
		}
	}

	public String getBaseURL() {
		return baseURL;
	}

	public String getGalleryhash() {
		return galleryhash;
	}

	@Override
	public String toString() {
		String s = galleryname;
		for (Picture picture : pictures) {
			if (picture != null) {
				s += "\n " + picture.name;
			}
		}
		return s;
	}

	public Picture[] getPictures() {
		return pictures;
	}

	public String[] getUsers() {
		return users;
	}

	public void setGalleryhash(String galleryhash) {
		this.galleryhash = galleryhash;
	}

	public String getGalleryDisplayName() {
		return galleryDisplayName;
	}

	public void setGalleryDisplayName(String galleryDisplayName) {
		this.galleryDisplayName = galleryDisplayName;
	}

	public String getGalleryDisplayIdentifier() {
		return galleryDisplayIdentifier;
	}

	public void setGalleryDisplayIdentifier(String galleryDisplayIdentifier) {
		this.galleryDisplayIdentifier = galleryDisplayIdentifier;
	}

	public String getGalleryDisplayNormDate() {
		return galleryDisplayNormDate;
	}

	public void setGalleryDisplayNormDate(String galleryDisplayNormDate) {
		this.galleryDisplayNormDate = galleryDisplayNormDate;
	}

}
