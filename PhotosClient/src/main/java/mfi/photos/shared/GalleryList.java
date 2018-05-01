package mfi.photos.shared;

@SuppressWarnings("unused")
public class GalleryList {

	private String user;
	private int posY;
	private String search;
	private Item list[];

	public GalleryList(String user, int size, int posY, String search) {
		this.list = new Item[size];
		this.user = user;
		this.posY = posY;
		this.search = search;
	}

	public void addItem(String key, String name, String identifier, String normDate, String hash, String[] users) {
		Item newItem = new Item();
		newItem.key = key;
		newItem.name = name;
		newItem.identifier = identifier;
		newItem.normDate = normDate;
		newItem.hash = hash;
		newItem.users = users;
		for (int i = 0; i < this.list.length; i++) {
			if (this.list[i] == null) {
				this.list[i] = newItem;
				break;
			}
		}
	}

	public Item[] getList() {
		return list;
	}

	public class Item {
		private String key;
		private String name;
		private String identifier;
		private String normDate;
		private String hash;
		private String[] users;

		public String getKey() {
			return key;
		}

		public String getHash() {
			return hash;
		}

		public String[] getUsers() {
			return users;
		}
	}

}
