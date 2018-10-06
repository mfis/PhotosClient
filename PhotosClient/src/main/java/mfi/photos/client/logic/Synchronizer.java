package mfi.photos.client.logic;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import mfi.photos.client.model.Album;
import mfi.photos.client.model.Photo;
import mfi.photos.client.model.SyncModel;
import mfi.photos.client.model.SyncStatus;

public class Synchronizer {

	private static List<String> PHOTO_TYPES = new ArrayList<>();
	static {
		PHOTO_TYPES.add("jpg");
		PHOTO_TYPES.add("jpeg");
		PHOTO_TYPES.add("png");
		PHOTO_TYPES.add("bmp");
		PHOTO_TYPES.add("heic");
	}

	private static List<String> VIDEO_TYPES = new ArrayList<>();
	static {
		VIDEO_TYPES.add("mp4");
		VIDEO_TYPES.add("mp2");
		VIDEO_TYPES.add("mpg");
		VIDEO_TYPES.add("mpeg");
		VIDEO_TYPES.add("mov");
		VIDEO_TYPES.add("avi");
		VIDEO_TYPES.add("vob");
		VIDEO_TYPES.add("m4v");
	}

	public void lookupSyncStatus(SyncModel syncModel, PhotosServerConnection photoServerConnection)
			throws Exception {

		syncModel.getAlbums().clear();

		// LOCAL
		File[] listFiles = new File(syncModel.getLocalBasePath())
				.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
		for (File file : listFiles) {
			Album album = new Album(file.getName(), keyFromName(file.getName()));
			readAlbum(album, file);
			if (album.getPhotos().size() > 0) {
				syncModel.getAlbums().add(album);
			}
		}
		syncModel.sortAlbums();

		// REMOTE AND COMPARING
		Map<String, String> remoteAlbumKeysAndHashes = photoServerConnection.readAlbumKeysAndHashes();
		for (Album album : syncModel.getAlbums()) {
			if (remoteAlbumKeysAndHashes.containsKey(album.getKey())) {
				// Album is remote available
				album.setHashRemote(remoteAlbumKeysAndHashes.get(album.getKey()));
				if (album.getHashLocal().equals(album.getHashRemote())) {
					// Album is remote in sync, nothing to do
					album.setSyncStatus(SyncStatus.SYNCHRON);
				} else {
					album.setSyncStatus(SyncStatus.INDEX_ASYNCHRON);
					photoServerConnection.readPhotos(album);
					for (Photo photo : album.getPhotos()) {
						if (StringUtils.isNotBlank(photo.getRemoteHash())) {
							// Photo is remote available
							if (photo.getLocalHash().equals(photo.getRemoteHash())) {
								// Photo is remote in sync, nothing to do
							} else {
								// Photo is NOT remote in sync
								album.getPhotoRemoteNamesOutOfSync().add(photo.getRemoteName(null));
								album.setSyncStatus(SyncStatus.PHOTOS_ASYNCHRON);
							}
						} else {
							// Photo is NOT remote available
							album.getPhotoRemoteNamesOutOfSync().add(photo.getRemoteName(null));
							album.setSyncStatus(SyncStatus.PHOTOS_ASYNCHRON);
						}
					}
				}
			} else {
				// Album is NOT remote available
				album.setHashRemote(null);
				album.setPhotoRemoteNamesOutOfSync(album.photoRemoteNamesAsList());
				album.setSyncStatus(SyncStatus.LOKAL);
			}
		}
	}

	public void renameLocalAlbum(SyncModel syncModel, String nameOld, String nameNew) {

		String pathBase = syncModel.getLocalBasePath();
		if (!StringUtils.endsWith(pathBase, "/")) {
			pathBase += "/";
		}
		File fileOld = new File(pathBase + nameOld);
		boolean ok = fileOld.renameTo(new File(pathBase + nameNew));
		if (!ok) {
			throw new RuntimeException("renaming not successful");
		}
	}

	private void readAlbum(Album album, File albumDir) {

		List<File> unfilteredList = (List<File>) FileUtils.listFiles(albumDir,
				FileFilterUtils.trueFileFilter(), FileFilterUtils.trueFileFilter());

		Map<String, File> filteredFiles = new HashMap<>();
		for (File file : unfilteredList) {
			if (file.isFile() && file.canRead() && !file.isHidden()) {
				String suffix = StringUtils.substringAfterLast(file.getName(), ".").toLowerCase();
				if (PHOTO_TYPES.contains(suffix) || VIDEO_TYPES.contains(suffix)) {
					if (!filteredFiles.containsKey(file.getName())) {
						filteredFiles.put(file.getName(), file);
					} else {
						album.increaseDoubletteCount();
					}
				}
			}
		}

		List<File> filteredList = new LinkedList<>();
		Set<String> names = new HashSet<>();
		for (File file : filteredFiles.values()) {
			if (!names.contains(file.getName())) {
				filteredList.add(file);
				names.add(file.getName());
			}
		}

		Collections.sort(filteredList, new FileNameComparator());

		for (File photoFile : filteredList) {
			Photo photo = new Photo(photoFile);
			String suffix = StringUtils.substringAfterLast(photoFile.getName(), ".").toLowerCase();
			photo.setVideo(VIDEO_TYPES.contains(suffix));
			calculatePhotoHash(photo, photoFile);
			album.getPhotos().add(photo);
		}

		album.setHashLocal(album.lookupAlbumHash());
	}

	private void calculatePhotoHash(Photo photo, File photoFile) {

		String syncStatusIdent;
		if (photo.isVideo()) {
			syncStatusIdent = photoFile.lastModified() + "#" + photoFile.length() + "#v3";
		} else {
			syncStatusIdent = photoFile.lastModified() + "#" + photoFile.length() + "#v1";
		}
		String syncStatusHash = org.apache.commons.codec.binary.Base64
				.encodeBase64URLSafeString(syncStatusIdent.getBytes(StandardCharsets.UTF_8));
		photo.setLocalHash(syncStatusHash);
	}

	public static String keyFromName(String name) {

		String albumKey = name;
		albumKey = StringUtils.replace(albumKey, "ä", "ae");
		albumKey = StringUtils.replace(albumKey, "ö", "oe");
		albumKey = StringUtils.replace(albumKey, "ü", "ue");
		albumKey = StringUtils.replace(albumKey, "Ä", "Ae");
		albumKey = StringUtils.replace(albumKey, "Ö", "Oe");
		albumKey = StringUtils.replace(albumKey, "Ü", "Ue");
		albumKey = StringUtils.replace(albumKey, "ß", "ss");
		albumKey = albumKey.replaceAll("[^a-zA-Z0-9]", " ");
		albumKey = WordUtils.capitalizeFully(albumKey);
		albumKey = StringUtils.replace(albumKey, " ", "");
		return albumKey;
	}

}
