package mfi.photos.client.logic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import mfi.photos.client.gui.Gui;
import mfi.photos.client.model.Album;
import mfi.photos.client.model.Dimension;
import mfi.photos.client.model.Photo;
import mfi.photos.client.model.SyncModel;
import mfi.photos.client.model.SyncStatus;
import mfi.photos.shared.GalleryView;

public class Processor {

	private Gui gui;
	private String[] users;
	private Properties properties;
	private PhotosServerConnection photoServerConnection;
	private Synchronizer synchronizer;
	private SyncModel syncModel;
	private EstimatedUploadTime estimatedUploadTime;
	private ImageProcessing imageProcessing;

	public Processor() {
		super();
	}

	public void initialize() throws Exception {

		properties = getApplicationProperties();
		imageProcessing = new ImageProcessing(properties.getProperty("ffmpegPath"),
				properties.getProperty("tempFilePath"));
		photoServerConnection = new PhotosServerConnection(properties.getProperty("serverURL"),
				properties.getProperty("technicalUser"), properties.getProperty("technicalUserPass"),
				properties.getProperty("encryptionSecret"));

		users = StringUtils.split(properties.getProperty("userlist"), ',');
		gui = new Gui(this);
		gui.paintGui(users);

		if (isConnectionToServerOK()) {
			checkSyncStatus();
		} else {
			gui.showConnectionError();
		}
	}

	public boolean isConnectionToServerOK() {
		return photoServerConnection.isConnectionOK();
	}

	public List<Integer> albumRowsOutOfSync() {

		List<Integer> rows = new LinkedList<>();
		for (int i = 0; i < syncModel.getAlbums().size(); i++) {
			if (syncModel.getAlbums().get(i).getSyncStatus() == SyncStatus.INDEX_ASYNCHRON
					|| syncModel.getAlbums().get(i).getSyncStatus() == SyncStatus.PHOTOS_ASYNCHRON) {
				rows.add(i);
			}
		}
		return rows;
	}

	public void renameAlbum(int index, String newAlbumName) {

		try {
			if (syncModel.getAlbums().get(index).getSyncStatus() != SyncStatus.LOKAL) {
				System.out.println("rename=" + syncModel.getAlbums().get(index).getName() + " new=" + newAlbumName);

				GalleryView galleryViewNew = photoServerConnection
						.readGalleryView(syncModel.getAlbums().get(index).getKey());
				galleryViewNew.setGalleryname(newAlbumName);
				galleryViewNew.setKey(Synchronizer.keyFromName(newAlbumName));
				galleryViewNew.setBaseURL(lookupGalleryViewBaseUrl(galleryViewNew.getKey()));

				photoServerConnection.renameGallery(syncModel.getAlbums().get(index).getKey(), galleryViewNew);
			}

			synchronizer.renameLocalAlbum(syncModel, syncModel.getAlbums().get(index).getName(), newAlbumName);

			gui.viewMessage("");
			checkSyncStatus();

		} catch (Exception ex) {
			gui.viewMessage("Es ist ein Fehler aufgetreten!");
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			System.out.println(sw.toString());
			gui.viewMessage(sw.toString());
			return;
		}
	}

	private void checkSyncStatus() throws Exception {

		gui.clearTable();

		syncModel = new SyncModel(properties.getProperty("localAlbumRoot"));
		synchronizer = new Synchronizer();
		synchronizer.lookupSyncStatus(syncModel, photoServerConnection);
		Map<String, List<String>> albumKeysAndUsers = photoServerConnection.readAlbumKeysAndUsers();

		int albumLocalCount = 0;
		int pictureLocalCount = 0;
		int albumOutOfSyncCount = 0;
		int pictureOutOfSyncCount = 0;
		int albumNoUsersCount = 0;

		System.out.println("checkSyncStatus for " + syncModel.getAlbums().size() + " albums");

		for (Album album : syncModel.getAlbums()) {
			if (album.getSyncStatus() == SyncStatus.LOKAL) {
				albumLocalCount++;
				pictureLocalCount += album.getPhotos().size();
			} else if (album.getSyncStatus() == SyncStatus.SYNCHRON) {
			} else if (album.getSyncStatus() == SyncStatus.INDEX_ASYNCHRON) {
				albumOutOfSyncCount++;
			} else if (album.getSyncStatus() == SyncStatus.PHOTOS_ASYNCHRON) {
				albumOutOfSyncCount++;
				pictureOutOfSyncCount += album.getPhotoRemoteNamesOutOfSync().size();
			} else {
				throw new RuntimeException("Unbekannter Status: " + album.getSyncStatus().name());
			}

			Boolean[] userFlags = new Boolean[users.length];
			for (int u = 0; u < users.length; u++) {
				if (albumKeysAndUsers.containsKey(album.getKey())) {
					userFlags[u] = albumKeysAndUsers.get(album.getKey()).contains(users[u]);
					if (u == 0 && albumKeysAndUsers.get(album.getKey()).size() == 0 && album.getHashRemote() != null) {
						albumNoUsersCount++;
					}
				} else {
					userFlags[u] = false;
				}
			}

			int videos = album.countVideos();
			String counter = (album.getPhotos().size() - videos) + " + " + videos;
			String status = album.getSyncStatus().label(album.getPhotoRemoteNamesOutOfSync().size());
			gui.addRow(album.getKey(), false, album.getName(), counter, status, userFlags);
		}

		StringBuilder sb = new StringBuilder();
		if (albumLocalCount > 0) {
			sb.append(albumLocalCount);
			sb.append(" ");
			sb.append(albumLocalCount == 1 ? "Album" : "Alben");
			sb.append(" mit insgesamt ");
			sb.append(pictureLocalCount);
			sb.append(" ");
			sb.append(pictureLocalCount == 1 ? "Datei" : "Dateien");
			sb.append(" nur lokal vorhanden.  ");
		}
		if (albumNoUsersCount > 0) {
			sb.append(albumNoUsersCount);
			sb.append(" ");
			sb.append(albumNoUsersCount == 1 ? "Album" : "Alben");
			sb.append(" Online ohne Berechtigungen.");
		}
		sb.append("\n");
		if (albumOutOfSyncCount > 0) {
			sb.append(albumOutOfSyncCount);
			sb.append(" ");
			sb.append(albumOutOfSyncCount == 1 ? "Album" : "Alben");
			sb.append(" mit insgesamt ");
			sb.append(pictureOutOfSyncCount);
			sb.append(" ");
			sb.append(pictureOutOfSyncCount == 1 ? "Datei" : "Dateien");
			sb.append(" nicht synchron.\n");
		}
		if (albumLocalCount == 0 && albumOutOfSyncCount == 0) {
			sb.append("Alle Alben sind synchron.");
		}
		gui.appendMessage(sb.toString().trim());
	}

	public void syncAlbumsByKeyAndUsers(Map<String, Boolean[]> keyAndUsers) throws Exception {

		if (!isConnectionToServerOK()) {
			gui.showConnectionError();
			return;
		}

		int totalPhotosToProcess = 0;
		long totalFilesize = 0;
		int totalPhotosProcessed = 0;
		int totalExceptions = 0;
		for (String key : keyAndUsers.keySet()) {
			Album album = syncModel.lookupAlbumByKey(key);
			totalPhotosToProcess += album.getPhotoRemoteNamesOutOfSync().size();
			totalFilesize += album.countFileSizeOutOfSync();
		}

		estimatedUploadTime = new EstimatedUploadTime(keyAndUsers.size(), totalFilesize);
		estimatedUploadTime.startUpload();

		int i = 1;
		for (String key : keyAndUsers.keySet()) {
			System.out.println("sync: " + key);
			try {
				List<String> albumUser = new LinkedList<>();
				for (int u = 0; u < users.length; u++) {
					if (keyAndUsers.get(key)[u] == true) {
						albumUser.add(users[u]);
					}
				}
				Album album = syncModel.lookupAlbumByKey(key);
				totalExceptions += processAlbum(album, syncModel.getLocalBasePath(),
						"Album " + i + " von " + keyAndUsers.keySet().size(), totalPhotosToProcess,
						totalPhotosProcessed, albumUser.toArray(new String[albumUser.size()]));
				totalPhotosProcessed += album.getPhotoRemoteNamesOutOfSync().size();
			} catch (Exception ex) {
				gui.viewMessage("Es ist ein Fehler aufgetreten!");
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				ex.printStackTrace(pw);
				System.out.println(sw.toString());
				gui.viewMessage(key + " - " + sw.toString());
				return;
			}

			if (gui.isCancel()) {
				break;
			}

			estimatedUploadTime.completedAlbumUpload();
			i++;
		}
		estimatedUploadTime.completed();

		if (totalExceptions > 0 || gui.isCancel()) {
			gui.viewMessage(gui.isCancel() ? "Abgebrochen!"
					: "Fertig!" + (totalExceptions > 0 ? " - Insgesamt " + totalExceptions + " Fehler." : ""));
		}
		{
			gui.viewMessage("");
		}
		checkSyncStatus();
		photoServerConnection.cleanUp(syncModel);
	}

	private int processAlbum(Album album, String localBasePath, String albumStatus, int totalPhotosToProcess,
			int totalPhotosProcessed, String[] users) throws Exception {

		if (album.getPhotoRemoteNamesOutOfSync().size() == 0 && album.getPhotos().size() == 0
				&& album.getHashRemote() == null) {
			// album is local only and empty -> no upload
			return 0;
		}

		String baseUrl = lookupGalleryViewBaseUrl(album.getKey());

		GalleryView galleryView = new GalleryView(album.getKey(), album.getName(), album.getPhotos().size(), users,
				baseUrl, album.lookupAlbumHash());

		int j = 0;

		StringBuilder exceptionString = new StringBuilder();
		int exceptionCounter = 0;

		for (Photo photo : album.getPhotos()) {

			if (gui.isCancel()) {
				galleryView.setGalleryhash("canceled!");
				break;
			}

			boolean upload = album.getPhotoRemoteNamesOutOfSync().contains(photo.getRemoteName(null));

			try {
				if (upload) {

					j++;
					viewStatusMessage(album, albumStatus, totalPhotosToProcess, totalPhotosProcessed, j,
							exceptionCounter);

					BufferedImage transformedImage;
					ResizedImage resizedImage;
					String fullSizeImageName;
					long remoteFileSize = 0;

					if (photo.isVideo()) {

						// video preview
						fullSizeImageName = photo.getRemoteName("pre_");
						resizedImage = imageProcessing.createPreviewImage(photo, 720);
						transformedImage = ImageIO.read(new ByteArrayInputStream(resizedImage.getBytes()));
						Dimension dimVideo = new Dimension(resizedImage.getWidth(), resizedImage.getHeight());

						// video
						File resizedVideo = imageProcessing.resizeVideo(photo, dimVideo, 720);
						remoteFileSize = resizedVideo.length();
						photoServerConnection.uploadPhoto(new FileInputStream(resizedVideo), resizedVideo.length(),
								photo.getRemoteName(null), album.getKey());

					} else {

						// full size image
						fullSizeImageName = photo.getRemoteName(null);
						transformedImage = imageProcessing.rotateImageToZeroDegree(photo);
						resizedImage = imageProcessing.resizeImage(transformedImage, 1080, false);
						remoteFileSize = resizedImage.getBytes().length;

					}

					// upload full size image / preview
					photoServerConnection.uploadPhoto(new ByteArrayInputStream(resizedImage.getBytes()),
							resizedImage.getBytes().length, fullSizeImageName, album.getKey());

					// thumbnail image
					ResizedImage thumbnailImage = imageProcessing.resizeImage(transformedImage, 90, false);
					String thumbnailName = photo.getRemoteName("tn_");
					photoServerConnection.uploadPhoto(new ByteArrayInputStream(thumbnailImage.getBytes()),
							thumbnailImage.getBytes().length, thumbnailName, album.getKey());

					// add item
					galleryView.addItem(photo.getRemoteName(null), thumbnailImage.getHeight(),
							thumbnailImage.getWidth(), resizedImage.getHeight(), resizedImage.getWidth(),
							photo.getLocalHash(), remoteFileSize);

					estimatedUploadTime.completedPhotoUpload(photo.getLocalFile().length());

				} else {
					if (!album.isHasRemotePhotoData()) {
						photoServerConnection.readPhotos(album);
					}
					galleryView.addItem(photo.getRemoteName(null), photo.getRemoteThumbnailSize().getHeight(),
							photo.getRemoteThumbnailSize().getWidth(), photo.getRemoteSize().getHeight(),
							photo.getRemoteSize().getWidth(), photo.getLocalHash(), photo.getRemoteFileSize());
				}

			} catch (Exception e) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				System.out.println(photo.getLocalFile().getAbsolutePath() + " - " + sw.toString());
				exceptionString.append(System.getProperty("java.version") + " " + System.getProperty("java.home")
						+ " Error processing image: " + album.getName() + ": " + photo.getLocalName() + ". "
						+ sw.toString());
				exceptionCounter++;
			}
		}

		if (exceptionCounter == 0) {
			photoServerConnection.sendGalleryView(galleryView);
		} else {
			throw new RuntimeException(exceptionString.toString());
		}

		return exceptionCounter;
	}

	private String lookupGalleryViewBaseUrl(String albumKey) {
		String rootDirName = properties.getProperty("uploadRootDirName");
		String rootUrl = properties.getProperty("uploadRootURL");
		String baseUrl = rootUrl + "/" + rootDirName + "/" + albumKey + "/";
		return baseUrl;
	}

	private void viewStatusMessage(Album album, String albumStatus, int totalPhotosToProcess, int totalPhotosProcessed,
			int j, int exceptionCounter) {

		String msg = albumStatus + ",   Datei " + j + " von " + album.getPhotoRemoteNamesOutOfSync().size();
		msg += ",   Gesamt " + (totalPhotosProcessed + j) + " von " + totalPhotosToProcess;
		if (exceptionCounter > 0) {
			msg += ", " + exceptionCounter + " Fehler";
		}
		String est = estimatedUploadTime.estimatedTime();
		if (est != null) {
			msg += "\n" + est + " verbleibend";
		}
		gui.viewMessage(msg);
	}

	public void exit() {
		FileUtils.deleteQuietly(new File(properties.getProperty("tempFilePath") + "/photos_frame.bmp"));
		FileUtils.deleteQuietly(new File(properties.getProperty("tempFilePath") + "/photos_resized.mp4"));
		System.exit(0);
	}

	public String[] getUsers() {
		return users;
	}

	public Properties getApplicationProperties() {

		Properties properties = new Properties();
		try {
			File file = new File(System.getProperty("user.home") + "/documents/config/photosclient2.properties");
			properties.load(new FileInputStream(file));
			return properties;
		} catch (Exception e) {
			throw new RuntimeException("Properties could not be loaded", e);
		}
	}

}
