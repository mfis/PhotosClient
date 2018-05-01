package mfi.photos.client.logic;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.jpeg.JpegDirectory;

import mfi.photos.client.model.Dimension;
import mfi.photos.client.model.Photo;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.builder.FFmpegOutputBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;

public class ImageProcessing {

	private static final long BASE_VIDEO_BITRATE = 2_621_440L;
	private static final String DEST_PIXEL_FORMAT = "yuv420p";
	private static final List<String> NON_SUPPORTED_PIXEL_FORMATS = new LinkedList<>();
	static {
		NON_SUPPORTED_PIXEL_FORMATS.add("yuvj422p"); // lower case!
	}

	private String ffmpegDir;
	private String tempFileDir;

	public ImageProcessing(String ffmpegDir, String tempFilePath) {
		this.ffmpegDir = ffmpegDir;
		this.tempFileDir = tempFilePath;
	}

	public ResizedImage createPreviewImage(Photo photo, int maxPixelSmallerSide)
			throws IOException, InterruptedException {

		String ffmpegPath = ffmpegDir + "/ffmpeg";
		String inPath = photo.getLocalFile().getAbsolutePath();
		String outPath = tempFileDir + "/photos_frame.bmp";

		FileUtils.deleteQuietly(new File(outPath));

		ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-i", inPath, "-ss", "00:00:00.000", "-vframes", "1",
				outPath);
		Process p = pb.start();

		if (p.waitFor() > 1) {
			throw new IOException("error creating single frame: " + p.exitValue());
		}
		StringBuilder sb = new StringBuilder();
		BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
		BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		String x;
		do {
			x = in.readLine();
			if (x != null) {
				sb.append(x + "\n");
			}
		} while (x != null);
		do {
			x = er.readLine();
			if (x != null) {
				sb.append(x + "\n");
			}
		} while (x != null);

		if (!new File(outPath).exists()) {
			throw new IOException("error creating single frame: " + sb.toString());
		}

		BufferedImage large = ImageIO.read(new File(tempFileDir + "/photos_frame.bmp"));
		BufferedImage small = readPlaySymbolImage();

		Dimension newSizeLarge = calculateExactSize(new Dimension(large.getWidth(), large.getHeight()), 720);
		ResizedImage resizedFrame = resizeImage(large, newSizeLarge, false);
		BufferedImage largeResized = ImageIO.read(new ByteArrayInputStream(resizedFrame.getBytes()));

		int pixelsPlaySymbol = Math.min(resizedFrame.getHeight(), resizedFrame.getWidth()) / 7 * 3;
		ResizedImage resizedPlaySymbol = resizeImage(small, pixelsPlaySymbol, true);
		BufferedImage smallResized = ImageIO.read(new ByteArrayInputStream(resizedPlaySymbol.getBytes()));

		BufferedImage combined = new BufferedImage(resizedFrame.getWidth(), resizedFrame.getHeight(),
				BufferedImage.TYPE_INT_RGB);
		Graphics g = combined.getGraphics();
		g.drawImage(largeResized, 0, 0, null);
		g.drawImage(smallResized, (resizedFrame.getWidth() - resizedPlaySymbol.getWidth()) / 2,
				(resizedFrame.getHeight() - resizedPlaySymbol.getHeight()) / 2, null);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(combined, Photo.DEST_PIC_DATATYPE, baos);
		baos.flush();
		byte[] imageInByte = baos.toByteArray();
		ResizedImage previewImage = new ResizedImage();
		previewImage.setBytes(imageInByte);
		previewImage.setHeight(resizedFrame.getHeight());
		previewImage.setWidth(resizedFrame.getWidth());
		baos.close();
		return previewImage;
	}

	public File resizeVideo(Photo photo, Dimension newSize, int basePixelSize)
			throws IOException, InterruptedException {

		FileUtils.deleteQuietly(new File(tempFileDir + "/photos_resized.mp4"));

		FFprobe ffprobe = new FFprobe(ffmpegDir + "/ffprobe");
		FFmpeg ffmpeg = new FFmpeg(ffmpegDir + "/ffmpeg");
		FFmpegProbeResult probeResult = ffprobe.probe(photo.getLocalFile().getAbsolutePath());
		FFmpegStream streamVideo = null;
		for (int i = 0; i < probeResult.getStreams().size(); i++) {
			if (probeResult.getStreams().get(i).height > 0 && probeResult.getStreams().get(i).width > 0) {
				streamVideo = probeResult.getStreams().get(i);
				break;
			}
		}
		if (streamVideo == null) {
			throw new IOException("No video stream found: " + photo.getLocalFile().getAbsolutePath());
		}

		String codec = StringUtils.remove(streamVideo.codec_name + " " + streamVideo.codec_long_name, ".");

		long bitrate = streamVideo.bit_rate > 0 ? streamVideo.bit_rate : streamVideo.max_bit_rate;
		long targetBitrate = calculateTargetBitrate(basePixelSize, newSize);

		if (bitrate <= targetBitrate && StringUtils.endsWithIgnoreCase(photo.getLocalFile().getName(), ".mp4")
				&& StringUtils.containsIgnoreCase(codec, "H264")) {
			// same format, lower bitrate ; keep file
			return photo.getLocalFile();
		}

		boolean convertPixelFormat = NON_SUPPORTED_PIXEL_FORMATS.contains(streamVideo.pix_fmt.toLowerCase());

		FFmpegOutputBuilder outputBuilder = new FFmpegBuilder().setInput(probeResult) //
				.overrideOutputFiles(true) //
				.addOutput(tempFileDir + "/photos_resized.mp4") //
				.setFormat("mp4") //
				.disableSubtitle() //
				.setAudioChannels(1) //
				.setAudioCodec("aac") //
				.setAudioBitRate(65536) //
				.setVideoCodec("h264") //
				.setVideoResolution(newSize.getWidth(), newSize.getHeight()) //
				.setVideoBitRate(targetBitrate) //
				.addExtraArgs("-preset", "slow") //
				.addExtraArgs("-profile:v", "high", "-level", "4.2"); //
		if (convertPixelFormat) {
			outputBuilder.addExtraArgs("-pix_fmt", DEST_PIXEL_FORMAT);
		}
		FFmpegBuilder fmpegBuilder = outputBuilder.done();

		FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
		FFmpegJob job = executor.createJob(fmpegBuilder, new ProgressListener() {
			final double duration_ms = probeResult.getFormat().duration * TimeUnit.SECONDS.toMillis(10L);

			@Override
			public void progress(Progress progress) {
				double percentageDouble = progress.out_time_ms / duration_ms;
				int percentage = (int) percentageDouble;
				System.out.println(percentage);
			}
		});
		job.run();
		return new File(tempFileDir + "/photos_resized.mp4");
	}

	private long calculateTargetBitrate(int maxPixelSmallerSide, Dimension newSize) {
		return BASE_VIDEO_BITRATE;
	}

	public BufferedImage rotateImageToZeroDegree(Photo photo)
			throws IOException, MetadataException, ImageProcessingException, Exception {

		BufferedImage bufferedImage = ImageIO.read(photo.getLocalFile());
		BufferedImage transformedImage;

		ImageInformation imageInformation = readImageInformation(photo.getLocalFile());
		if (imageInformation != null) {
			AffineTransform affineTransform = getExifTransformation(imageInformation);
			transformedImage = transformImage(bufferedImage, affineTransform);
		} else {
			transformedImage = bufferedImage;
		}
		return transformedImage;
	}

	public ResizedImage resizeImage(BufferedImage originalImage, int maxPixelSmallerSide, boolean withAlpha) {

		Dimension newSize = calculateOptimalSize(new Dimension(originalImage.getWidth(), originalImage.getHeight()),
				maxPixelSmallerSide, false);

		return resizeImage(originalImage, newSize, withAlpha);
	}

	public ResizedImage resizeImage(BufferedImage originalImage, Dimension newSize, boolean withAlpha) {

		try {
			ResizedImage resizedImage = new ResizedImage();
			resizedImage.setHeight(newSize.getHeight());
			resizedImage.setWidth(newSize.getWidth());

			Image toolkitImage = originalImage.getScaledInstance(newSize.getWidth(), newSize.getHeight(),
					Image.SCALE_SMOOTH);
			int width = toolkitImage.getWidth(null);
			int height = toolkitImage.getHeight(null);
			BufferedImage rbi = new BufferedImage(width, height,
					withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
			Graphics g = rbi.getGraphics();
			g.drawImage(toolkitImage, 0, 0, null);
			g.dispose();

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(rbi, Photo.DEST_PIC_DATATYPE, baos);
			baos.flush();
			byte[] imageInByte = baos.toByteArray();
			resizedImage.setBytes(imageInByte);
			baos.close();
			return resizedImage;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Dimension calculateOptimalSize(Dimension original, int maxPixelSmallerSide, boolean isVideo) {

		int newH = original.getHeight();
		int newW = original.getWidth();

		if (original.getWidth() > original.getHeight()) { // horizontal
			if (original.getHeight() > maxPixelSmallerSide) {
				newH = maxPixelSmallerSide;
				double factor = newH / (double) original.getHeight();
				newW = (int) (original.getWidth() * factor);
			}
		} else { // vertical
			if (original.getWidth() > maxPixelSmallerSide) {
				newW = maxPixelSmallerSide;
				double factor = newW / (double) original.getWidth();
				newH = (int) (original.getHeight() * factor);
			}
		}

		if (isVideo) {
			if (newH % 2 != 0) {
				newH++;
			}
			if (newW % 2 != 0) {
				newW++;
			}
		}

		return new Dimension(newW, newH);
	}

	private Dimension calculateExactSize(Dimension original, int pixelSmallerSide) {

		int newH = original.getHeight();
		int newW = original.getWidth();

		if (original.getWidth() > original.getHeight()) { // horizontal
			newH = pixelSmallerSide;
			double factor = newH / (double) original.getHeight();
			newW = (int) Math.round(original.getWidth() * factor);
		} else { // vertical
			newW = pixelSmallerSide;
			double factor = newW / (double) original.getWidth();
			newH = (int) Math.round(original.getHeight() * factor);
		}

		if (newH % 2 != 0) {
			newH++;
		}
		if (newW % 2 != 0) {
			newW++;
		}

		return new Dimension(newW, newH);
	}

	private static BufferedImage transformImage(BufferedImage image, AffineTransform transform) throws Exception {

		AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC);

		BufferedImage destinationImage = op.createCompatibleDestImage(image,
				(image.getType() == BufferedImage.TYPE_BYTE_GRAY) ? image.getColorModel() : null);
		Graphics2D g = destinationImage.createGraphics();
		g.setBackground(Color.WHITE);
		g.clearRect(0, 0, destinationImage.getWidth(), destinationImage.getHeight());
		destinationImage = op.filter(image, destinationImage);
		return destinationImage;
	}

	private static ImageInformation readImageInformation(File imageFile)
			throws IOException, MetadataException, ImageProcessingException {
		Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
		Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
		JpegDirectory jpegDirectory = metadata.getFirstDirectoryOfType(JpegDirectory.class);

		if (directory == null) {
			return null;
		}

		int orientation = 1;
		try {
			orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
		} catch (MetadataException me) {
			System.out.println("Could not get orientation");
		}
		int width = jpegDirectory.getImageWidth();
		int height = jpegDirectory.getImageHeight();

		return new ImageInformation(orientation, width, height);
	}

	private static AffineTransform getExifTransformation(ImageInformation info) {

		AffineTransform t = new AffineTransform();

		switch (info.orientation) {
		case 1:
			break;
		case 2: // Flip X
			t.scale(-1.0, 1.0);
			t.translate(-info.width, 0);
			break;
		case 3: // PI rotation
			t.translate(info.width, info.height);
			t.rotate(Math.PI);
			break;
		case 4: // Flip Y
			t.scale(1.0, -1.0);
			t.translate(0, -info.height);
			break;
		case 5: // - PI/2 and Flip X
			t.rotate(-Math.PI / 2);
			t.scale(-1.0, 1.0);
			break;
		case 6: // -PI/2 and -width
			t.translate(info.height, 0);
			t.rotate(Math.PI / 2);
			break;
		case 7: // PI/2 and Flip
			t.scale(-1.0, 1.0);
			t.translate(-info.height, 0);
			t.translate(0, info.width);
			t.rotate(3 * Math.PI / 2);
			break;
		case 8: // PI / 2
			t.translate(0, info.width);
			t.rotate(3 * Math.PI / 2);
			break;
		}

		return t;
	}

	private BufferedImage readPlaySymbolImage() throws IOException {
		URL url = this.getClass().getClassLoader().getResource("play.png");
		URLConnection resConn = url.openConnection();
		resConn.setUseCaches(false);
		InputStream in = resConn.getInputStream();
		return ImageIO.read(in);
	}

	// Inner class containing image information
	private static class ImageInformation {
		public final int orientation;
		public final int width;
		public final int height;

		public ImageInformation(int orientation, int width, int height) {
			this.orientation = orientation;
			this.width = width;
			this.height = height;
		}

		@Override
		public String toString() {
			return String.format("%dx%d,%d", this.width, this.height, this.orientation);
		}
	}
}
