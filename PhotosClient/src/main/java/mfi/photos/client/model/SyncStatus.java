package mfi.photos.client.model;

public enum SyncStatus {

	LOKAL("lokal", false), //
	SYNCHRON("synchron", false), //
	INDEX_ASYNCHRON("nicht synchron", false), //
	PHOTOS_ASYNCHRON("nicht synchron", true), //
	;

	private String label;

	private boolean countInLabel;

	private SyncStatus(String label, boolean countInLabel) {
		this.label = label;
		this.countInLabel = countInLabel;
	}

	public String label(int outOfSyncCount) {
		if (countInLabel) {
			return outOfSyncCount + " " + label;
		} else {
			return label;
		}
	}
}
