package mfi.photos.client.main;

import javax.swing.SwingUtilities;

import mfi.photos.client.logic.Processor;

public class PhotosClient {

	public static void main(String[] args) {

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					new Processor().initialize();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
}
