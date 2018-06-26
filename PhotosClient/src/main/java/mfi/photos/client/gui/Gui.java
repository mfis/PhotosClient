package mfi.photos.client.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import mfi.photos.client.logic.Processor;

public class Gui extends JFrame implements ActionListener {

	private static final long serialVersionUID = 1L;

	private JPanel contentPane;

	private JButton buttonNorth;

	private JButton buttonCancel;

	private JTable table;

	private JScrollPane tableScroll;

	private JTextArea message;

	private DefaultTableModel tableModel;

	private Processor processor;

	private int userCount = 0;

	private String messageText = "";

	private final javax.swing.Timer timer = new javax.swing.Timer(100, this);

	private boolean cancel = false;

	private String mto = "";

	public Gui(Processor processor) {
		this.processor = processor;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(timer) && !StringUtils.equals(messageText, mto)) {
			message.setText(messageText);
			mto = messageText;
			return;
		}
	}

	private class ProcessThread extends Thread {
		@Override
		public void run() {
			cancel = false;
			messageText = "";
			buttonNorth.setEnabled(false);
			buttonNorth.setVisible(false);
			buttonCancel.setEnabled(true);
			buttonCancel.setVisible(true);
			table.setEnabled(false);
			int scrollPos = tableScroll.getVerticalScrollBar().getValue();

			Map<String, Boolean[]> keyAndUsersToSync = new LinkedHashMap<>();
			int rowCount = tableModel.getRowCount();
			for (int i = 0; i < rowCount; i++) {
				if (tableModel.getValueAt(i, 1).equals(Boolean.TRUE)) {
					Boolean[] userFlags = new Boolean[userCount];
					for (int col = 5; col < 5 + userCount; col++) {
						userFlags[col - 5] = (Boolean) tableModel.getValueAt(i, col);
					}
					keyAndUsersToSync.put((String) tableModel.getValueAt(i, 0), userFlags);
				}
			}
			try {
				processor.syncAlbumsByKeyAndUsers(keyAndUsersToSync);
			} catch (Exception e) {
				e.printStackTrace();
			}

			table.setEnabled(true);
			buttonNorth.setEnabled(true);
			buttonNorth.setVisible(true);
			buttonCancel.setEnabled(false);
			buttonCancel.setVisible(false);
			System.out.println("new row count: " + tableModel.getRowCount());

			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					try {
						tableScroll.getVerticalScrollBar().setValue(scrollPos);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
	};

	public void paintGui(String[] user) {

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}

		userCount = user.length;

		JFrame frame = new JFrame("Photos Client");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);
		frame.setResizable(true);

		frame.setSize(new Dimension(1200, 680));
		frame.setPreferredSize(new Dimension(1200, 680));

		List<Image> icons = new LinkedList<Image>();
		icons.add(imageFromFileName("apple-touch-icon-64x64-precomposed.png"));
		icons.add(imageFromFileName("apple-touch-icon-128x128-precomposed.png"));
		frame.setIconImages(icons);
		this.setIconImages(icons);
		tryToSetMacOSDockImage(icons);

		contentPane = new JPanel();
		contentPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		contentPane.setLayout(new BorderLayout(5, 5));

		buttonNorth = new JButton("Synchronisieren");
		buttonNorth.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				buttonNorth.setEnabled(false);
				ProcessThread processThread = new ProcessThread();
				processThread.setDaemon(true);
				processThread.start();
			}
		});
		JPanel buttonPane = new JPanel();
		buttonPane.add(buttonNorth);

		buttonCancel = new JButton("Abbrechen");
		buttonCancel.setEnabled(false);
		buttonCancel.setVisible(false);
		buttonCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				buttonCancel.setEnabled(false);
				cancel = true;
			}
		});
		buttonPane.add(buttonCancel);
		contentPane.add(buttonPane, BorderLayout.NORTH);

		String[] header = new String[] {};
		header = ArrayUtils.addAll(header, "", "Sync", "Album", "Photos + Videos", "Status");
		header = ArrayUtils.addAll(header, user);

		tableModel = new DefaultTableModel(header, 0) {

			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return column != 0 && column != 2 && column != 3 && column != 4;
			}

			@Override
			public Class<?> getColumnClass(int column) {
				switch (column) {
				case 0:
					return String.class;
				case 1:
					return Boolean.class;
				case 2:
					return String.class;
				case 3:
					return String.class;
				case 4:
					return String.class;
				default:
					return Boolean.class;
				}
			}
		};

		tableModel.addTableModelListener(new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent e) {
				if (e.getColumn() > 1) {
					if (tableModel.getValueAt(e.getFirstRow(), 1).equals(Boolean.FALSE)) {
						tableModel.setValueAt(true, e.getFirstRow(), 1);
					}
				}
			}
		});

		table = new JTable(tableModel) {

			private static final long serialVersionUID = 1L;

			@Override
			public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);
				c.setBackground(row % 2 == 0 ? new Color(235, 235, 235) : Color.WHITE);
				return c;
			}
		};

		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int col = table.columnAtPoint(e.getPoint());
				if (col == 2) {
					int row = table.getSelectedRow();
					String oldAlbumName = (String) tableModel.getValueAt(row, col);
					String newAlbumName = StringUtils
							.trimToNull((String) JOptionPane.showInputDialog(frame, "Neuer Album-Name:",
									"Album umbenennen", JOptionPane.PLAIN_MESSAGE, null, null, oldAlbumName));
					if (newAlbumName != null && !StringUtils.equals(oldAlbumName, newAlbumName)) {
						viewMessage("");
						processor.renameAlbum(row, newAlbumName);
					}
				}
			}
		});

		table.getTableHeader().setPreferredSize(new Dimension(100, 30));
		table.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int col = table.columnAtPoint(e.getPoint());
				if (col == 1) {
					List<Integer> rowsOutOfSync = processor.albumRowsOutOfSync();
					for (int row : rowsOutOfSync) {
						tableModel.setValueAt(true, row, 1);
					}
				}
			}
		});

		table.setRowSelectionAllowed(false);
		table.setColumnSelectionAllowed(false);
		table.setCellSelectionEnabled(false);
		table.setShowHorizontalLines(true);
		table.setShowGrid(true);
		table.setFocusable(false);
		table.setRowHeight(30);

		table.getColumnModel().getColumn(0).setMinWidth(0);
		table.getColumnModel().getColumn(0).setMaxWidth(0);

		table.getColumnModel().getColumn(1).setMinWidth(40);
		table.getColumnModel().getColumn(1).setMaxWidth(40);

		table.getColumnModel().getColumn(3).setMinWidth(100);
		table.getColumnModel().getColumn(3).setMaxWidth(100);

		table.getColumnModel().getColumn(4).setMinWidth(150);
		table.getColumnModel().getColumn(4).setMaxWidth(150);

		for (int i = 5; i < 5 + user.length; i++) {
			table.getColumnModel().getColumn(i).setMinWidth(55);
			table.getColumnModel().getColumn(i).setMaxWidth(55);
		}

		tableScroll = new JScrollPane(table);
		tableScroll.setVerticalScrollBar(new JScrollBar());
		tableScroll.getVerticalScrollBar().setUnitIncrement(8);

		contentPane.add(tableScroll, BorderLayout.CENTER);

		message = new JTextArea();
		message.setText("\nInitialisierung...\n");
		message.setRows(3);
		message.setEditable(false);
		Border textBorder = BorderFactory.createLineBorder(Color.WHITE);
		message.setBorder(BorderFactory.createCompoundBorder(textBorder, BorderFactory.createEmptyBorder(3, 3, 3, 3)));
		contentPane.add(message, BorderLayout.SOUTH);

		frame.getContentPane().add(contentPane, BorderLayout.CENTER);
		frame.pack();
		frame.setVisible(true);

		timer.start();
	}

	private void tryToSetMacOSDockImage(List<Image> icons) {
		// noop
	}

	private Image imageFromFileName(String filename) {
		try {
			URL url = this.getClass().getClassLoader().getResource(filename);
			URLConnection resConn = url.openConnection();
			resConn.setUseCaches(false);
			InputStream in = resConn.getInputStream();
			return ImageIO.read(in);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public void addRow(String key, Boolean sync, String album, String photoCount, String status, Boolean[] userFlag) {
		Object[] cols = new Object[] {};
		cols = ArrayUtils.addAll(cols, key, sync, album, photoCount, status);
		cols = ArrayUtils.addAll(cols, (Object[]) userFlag);
		tableModel.addRow(cols);
	}

	public void viewMessage(String messageString) {
		messageText = messageString;
	}

	public void appendMessage(String messageString) {
		messageText = messageText.trim();
		if (StringUtils.isBlank(messageText)) {
			messageText = messageString;
		} else {
			messageText = messageText + "\n" + messageString;
		}
	}

	public void showConnectionError() {
		JOptionPane.showMessageDialog(new JFrame(), "Photos Server ist nicht erreichbar.", "Verbindungsfehler",
				JOptionPane.WARNING_MESSAGE);
	}

	public void clearTable() {
		tableModel.setRowCount(0);
	}

	public boolean isCancel() {
		return cancel;
	}

}
