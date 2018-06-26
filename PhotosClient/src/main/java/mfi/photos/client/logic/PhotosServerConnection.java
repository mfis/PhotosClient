package mfi.photos.client.logic;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import mfi.photos.client.model.Album;
import mfi.photos.client.model.Dimension;
import mfi.photos.client.model.Photo;
import mfi.photos.client.model.SyncModel;
import mfi.photos.shared.AES;
import mfi.photos.shared.ChunkData;
import mfi.photos.shared.GalleryList;
import mfi.photos.shared.GalleryView;

public class PhotosServerConnection {

	private String url;
	private String credentialUser;
	private String credentialPass;
	private String encryptionSecret;

	public PhotosServerConnection(String url, String credentialUser, String credentialPass, String encryptionSecret) {
		this.url = url;
		this.credentialUser = credentialUser;
		this.credentialPass = credentialPass;
		this.encryptionSecret = encryptionSecret;
	}

	public void uploadPhoto(InputStream input, long length, String filename, String dir) {

		Consumer<ChunkData> uploadConsumer = (chunkData) -> {

			boolean append = chunkData.append;
			String asB64;
			if (chunkData.read == chunkData.bytesIns.length) {
				asB64 = Base64.getEncoder().encodeToString(chunkData.bytesIns);
			} else {
				asB64 = Base64.getEncoder().encodeToString(ArrayUtils.subarray(chunkData.bytesIns, 0, chunkData.read));
			}
			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("login_user", credentialUser);
			parameters.put("login_pass", credentialPass);
			parameters.put("galleryName", chunkData.dir);
			parameters.put("imageName", chunkData.filename);
			parameters.put("saveImage", asB64);
			parameters.put("append", String.valueOf(append));
			try {
				sendPost(parameters, Timeout.LONG);
			} catch (IOException | GeneralSecurityException e) {
				throw new RuntimeException("upload failed", e);
			}
		};

		long bytesWritten = AES.encrypt(length, encryptionSecret.toCharArray(), input, filename, dir, uploadConsumer);

		String checksumValue = Long.toString(bytesWritten);

		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("login_user", credentialUser);
		parameters.put("login_pass", credentialPass);
		parameters.put("galleryName", dir);
		parameters.put("imageName", filename);
		parameters.put("checksum", "");
		String checksumIs = "";
		try {
			checksumIs = sendPost(parameters, Timeout.SHORT);
		} catch (Exception e) {
			throw new RuntimeException("Error checksum file:", e);
		}
		if (!checksumIs.equals(checksumValue)) {
			throw new IllegalStateException("checksum error: " + filename + ": " + checksumIs + " / " + checksumValue);
		}
	}

	public void sendGalleryView(GalleryView galleryView)
			throws UnsupportedEncodingException, HttpException, IOException, GeneralSecurityException {

		System.out.println("sendGalleryView");
		galleryView.compressItems();
		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(galleryView);
		System.out.println(json);
		String asB64 = Base64.getEncoder().encodeToString(json.getBytes("utf-8"));
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("login_user", credentialUser);
		parameters.put("login_pass", credentialPass);
		parameters.put("saveGallery", asB64);
		sendPost(parameters, Timeout.SHORT);
	}

	public void renameGallery(String keyOld, GalleryView galleryViewNew)
			throws UnsupportedEncodingException, HttpException, IOException, GeneralSecurityException {

		System.out.println("renameGallery");
		galleryViewNew.compressItems();
		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(galleryViewNew);
		System.out.println(json);
		String asB64 = Base64.getEncoder().encodeToString(json.getBytes("utf-8"));
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("login_user", credentialUser);
		parameters.put("login_pass", credentialPass);
		parameters.put("renameGallery", asB64);
		parameters.put("keyOld", keyOld);
		sendPost(parameters, Timeout.SHORT);
	}

	public Map<String, String> readAlbumKeysAndHashes() throws Exception {

		System.out.println("readAlbumKeysAndHashes");

		Gson gson = new GsonBuilder().create();
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("login_user", credentialUser);
		parameters.put("login_pass", credentialPass);
		parameters.put("readlist", "");
		String respronse = sendPost(parameters, Timeout.SHORT);
		GalleryList galleryList = gson.fromJson(respronse, GalleryList.class);

		Map<String, String> keysAndHashes = new HashMap<>();
		for (GalleryList.Item item : galleryList.getList()) {
			keysAndHashes.put(item.getKey(), item.getHash());
		}

		return keysAndHashes;
	}

	public Map<String, List<String>> readAlbumKeysAndUsers() throws Exception {

		System.out.println("readAlbumKeysAndUsers");

		Gson gson = new GsonBuilder().create();
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("login_user", credentialUser);
		parameters.put("login_pass", credentialPass);
		parameters.put("readlist", "");
		String respronse = sendPost(parameters, Timeout.SHORT);
		GalleryList galleryList = gson.fromJson(respronse, GalleryList.class);

		Map<String, List<String>> keysAndUsers = new HashMap<>();
		for (GalleryList.Item item : galleryList.getList()) {
			keysAndUsers.put(item.getKey(), Arrays.asList(item.getUsers()));
		}

		return keysAndUsers;
	}

	public void readPhotos(Album album) throws Exception {

		System.out.println("readPhotoNamesAndHashes");

		GalleryView galleryView = readGalleryView(album.getKey());

		for (GalleryView.Picture item : galleryView.getPictures()) {
			Photo photo = album.lookupPhotoByRemoteName(item.getName());
			if (photo != null) {
				photo.setRemoteHash(item.getHash());
				photo.setRemoteSize(new Dimension(item.getW(), item.getH()));
				photo.setRemoteThumbnailSize(new Dimension(item.getTnw(), item.getTnh()));
				photo.setRemoteFileSize(item.getFileSize());
			}
		}

		album.setHasRemotePhotoData(true);
	}

	public GalleryView readGalleryView(String albumKey) throws HttpException, IOException, GeneralSecurityException {

		Gson gson = new GsonBuilder().create();
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("login_user", credentialUser);
		parameters.put("login_pass", credentialPass);
		parameters.put("readalbum", "");
		parameters.put("album_key", albumKey);
		String respronse = sendPost(parameters, Timeout.SHORT);
		GalleryView galleryView = gson.fromJson(respronse, GalleryView.class);
		return galleryView;
	}

	public void cleanUp(SyncModel syncModel)
			throws UnsupportedEncodingException, HttpException, IOException, GeneralSecurityException {

		System.out.println("cleanUp");

		GalleryList galleryList = new GalleryList(null, syncModel.getAlbums().size(), 0, null);
		for (Album album : syncModel.getAlbums()) {
			galleryList.addItem(album.getKey(), album.getName(), null, null, null, null);
		}
		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(galleryList);

		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("login_user", credentialUser);
		parameters.put("login_pass", credentialPass);
		parameters.put("cleanup", json);
		parameters.put("cleanupListHash", DigestUtils.md5Hex(json));
		sendPost(parameters, Timeout.LONG);
	}

	public boolean isConnectionOK() {

		System.out.println("isConnectionOK");

		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("login_user", credentialUser);
		parameters.put("login_pass", credentialPass);
		parameters.put("testConnection", "");
		try {
			sendPost(parameters, Timeout.SHORT);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private String sendPost(Map<String, String> parameters, Timeout timeout)
			throws HttpException, IOException, GeneralSecurityException {

		InputStream in = null;

		HttpClient client = new HttpClient();
		client.getParams().setParameter("http.connection.timeout", timeout.getWait());

		PostMethod method = new PostMethod(url);

		for (String key : parameters.keySet()) {
			method.addParameter(key, parameters.get(key));
		}

		int statusCode = client.executeMethod(method);

		if (statusCode != -1) {
			in = method.getResponseBodyAsStream();
		}
		if (statusCode != 200) {
			System.out.println("statusCode=" + statusCode);
			throw new IOException();
		}
		return StringUtils.trimToEmpty(IOUtils.toString(in));
	}

	private enum Timeout {

		LONG(30000), SHORT(3000);

		private Timeout(int wait) {
			this.wait = wait;
		}

		int wait;

		public int getWait() {
			return wait;
		}
	}

}
