package mfi.photos.client.logic;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;

public class FileNameComparator implements Comparator<File>, Serializable {
	private static final long serialVersionUID = 1L;
	private boolean caseSensitive = true;

	@Override
	public int compare(File o1, File o2) {
		try {
			File f1 = o1;
			File f2 = o2;

			if (f1.isDirectory() && !f2.isDirectory()) {
				return -1;
			} else if (!f1.isDirectory() && f2.isDirectory()) {
				return 1;
			} else if (caseSensitive) {
				return f1.getName().compareTo(f2.getName());
			} else {
				return f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase());
			}
		} catch (ClassCastException ex) {
		}

		return 0;
	}

	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	public void setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}
}