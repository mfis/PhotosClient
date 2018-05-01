package mfi.photos.client.model;

import java.util.Comparator;
import java.util.LinkedList;

public class SyncModel {

	public SyncModel(String localBasePath) {
		this.localBasePath = localBasePath;
		albums = new LinkedList<>();
	}

	private String localBasePath;

	private LinkedList<Album> albums;

	public void sortAlbums() {
		albums.sort(new AlbumComparator());
	}

	public Album lookupAlbumByKey(String key) {
		for (Album album : albums) {
			if (album.getKey().equals(key)) {
				return album;
			}
		}
		return null;
	}

	public String getLocalBasePath() {
		return localBasePath;
	}

	public void setLocalBasePath(String localBasePath) {
		this.localBasePath = localBasePath;
	}

	public LinkedList<Album> getAlbums() {
		return albums;
	}

	public void setAlbums(LinkedList<Album> albums) {
		this.albums = albums;
	}

	private class AlbumComparator implements Comparator<Album> {
		@Override
		public int compare(Album a, Album b) {
			return (a.getKey().compareTo(b.getKey()) * -1);
		}
	}

}
