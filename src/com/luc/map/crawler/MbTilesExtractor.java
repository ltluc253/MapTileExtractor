package com.luc.map.crawler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;


import com.luc.map.crawler.DownloadTask.OnTileAvailable;

public class MbTilesExtractor implements OnTileAvailable {
	
	private static final String INSERT_CMD = "INSERT INTO tiles (zoom_level,tile_column,tile_row,tile_data) VALUES (?,?,?,?)";
	public static String EXTRACTED_PATH = "Extracted";
	public static boolean ONLY_CRAWLER = false; 
	
	private MapAreaItem mMapAreaItem;
	private int fromZl, toZl;
	private int threadCount = 100;
	private ExecutorService executor;
	private int totalFile = 0;
	private int lastProgress = 0;
	private long startDownloadTime;
	private Connection mDbConnection = null;
	private PreparedStatement preparedStatement;
	private float mCompressQuality = 1.0f;
	
	public MbTilesExtractor(MapAreaItem mMapAreaItem, int fromZl, int toZl, int threadCount, float mCompressQuality) {
		this.mMapAreaItem = mMapAreaItem;
		this.fromZl = fromZl;
		this.toZl = toZl;
		this.threadCount = threadCount;
		this.mCompressQuality = mCompressQuality;
	}

	private void prepareDb() {
		String dbFileName = EXTRACTED_PATH + File.separator + mMapAreaItem.getFilename() + ".mbtiles";
	      File folder = new File("Extracted");
	      if (!folder.exists()) {
	    	  folder.mkdirs();
	      }
	      File dbFile = new File(dbFileName);
	      if (dbFile.exists()) {
	    	  dbFile.delete();
	      }
	      try {
	         Class.forName("org.sqlite.JDBC");
	         mDbConnection = DriverManager.getConnection("jdbc:sqlite:" + dbFileName);
	         mDbConnection.setAutoCommit(false);
	         
	         String createTableCmd = "CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);";
	         Statement mStatement = mDbConnection.createStatement();
	         mStatement.executeUpdate(createTableCmd);
	         mDbConnection.commit();
	      } catch ( Exception e ) {
	         System.err.println( e.getClass().getName() + ": " + e.getMessage() );
	      }
	}

	private void closeDb() {
		if (mDbConnection != null) {
			try {
				preparedStatement.close();
				mDbConnection.commit();
				mDbConnection.close();
			}catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
	}
	
	public void startExtract() {
		System.out.println("Start extract mbtiles of city: " + mMapAreaItem.getTitle());
		if (!ONLY_CRAWLER) {
			prepareDb();
		}
		double west = Double.parseDouble(mMapAreaItem.getBbox().get(0));
		double south = Double.parseDouble(mMapAreaItem.getBbox().get(1));
		double east = Double.parseDouble(mMapAreaItem.getBbox().get(2));
		double north = Double.parseDouble(mMapAreaItem.getBbox().get(3));
		totalFile = 0;
		startDownloadTime = System.currentTimeMillis();
		List<TileItem> cordinates = new ArrayList<>();
		for (int zoomLevel = fromZl; zoomLevel <= toZl; zoomLevel++) {
			int maxXY = (int) Math.pow(2, zoomLevel) - 1;
			//x range
			int fromX = (int) (maxXY * (west + 180) / 360);
			int toX = (int) (maxXY * (east + 180) / 360);
			double southRad = south * Math.PI / 180;
			int toY = (int) (maxXY * (1.0 - Math.log(Math.tan(southRad) + (1 / Math.cos(southRad))) / Math.PI) / 2.0);
			toY = toY > maxXY ? maxXY : toY;
			double northRad = north * Math.PI / 180;
			int fromY = (int) (maxXY * (1.0 - Math.log(Math.tan(northRad) + (1 / Math.cos(northRad))) / Math.PI) / 2.0);
			fromY = fromY < 0 ? 0 : fromY > toY ? toY : fromY;
			totalFile += ((toX - fromX + 1) * (toY - fromY + 1));
			cordinates.add(new TileItem(zoomLevel, fromX, toX, fromY, toY));
		}
		System.out.println("Total file download: " + totalFile);
		executor = Executors.newFixedThreadPool(threadCount);
		DownloadTask.sSuccessCount = 0;
		DownloadTask.sFailCount = 0;
		for (int i = 0; i < threadCount; i++) {
			DownloadTask task = new DownloadTask(cordinates, i, threadCount);
			task.setmOnTileAvailable(this);
			executor.execute(task);
		}
        executor.shutdown();
        try {
        	executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (Exception e) {
			// TODO: handle exception
        	e.printStackTrace();
		} finally {
			if (!ONLY_CRAWLER) {
				closeDb();
			}
		}
	}
	
	public boolean isProcessing() {
		return executor != null && !executor.isTerminated();
	}

	@Override
	public void onTileFileAvailable(int zoomLv, int x, int y, String filePath) {
		// TODO Auto-generated method stub
		if (!ONLY_CRAWLER) {
			insertTiles(zoomLv, x, y, filePath);
		}
		printProgress();
	}
	
	private synchronized void insertTiles(int zoomLv, int x, int y, String filePath) {
		File tileFile = new File(filePath);
		if (mDbConnection != null && tileFile.exists()) {
			try {
				preparedStatement = mDbConnection.prepareStatement(INSERT_CMD);
				preparedStatement.setInt(1, zoomLv);
				preparedStatement.setInt(2, x);
				preparedStatement.setInt(3, swapRow(zoomLv, y));
				preparedStatement.setBytes(4, getTileData(filePath, mCompressQuality));
				preparedStatement.executeUpdate();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void printProgress() {
		int progress = (DownloadTask.sSuccessCount + DownloadTask.sFailCount) * 100 / totalFile;
    	if (progress != lastProgress) {
    		lastProgress = progress;
    		long pastTime = System.currentTimeMillis() - startDownloadTime;
    		long remainTimeMs = (100 - lastProgress) * (pastTime / lastProgress);
    		System.out.println("Download progress: " + lastProgress + " --- Remain Time: " + formatTime(remainTimeMs / 1000) + " --- Past Time: " + formatTime(pastTime / 1000));
    	}
	}
	
	private static String formatTime(long timeInSecond) {
        int hour = (int) (timeInSecond / 3600);
        int minute = (int) ((timeInSecond % 3600) / 60);
        int second = (int) (timeInSecond - hour * 3600 - minute * 60);
        return String.format("%2d:%2d:%2d", hour, minute, second);
	}
	
	public static byte[] getTileData(String tilePath, float quality) {
		String fileExtentions = tilePath.lastIndexOf(".") > 0 ? tilePath.substring(tilePath.lastIndexOf(".") + 1)  : "png";
		if (quality > 0 && quality < 1 && "jpg".equals(fileExtentions)) {
			return getCompressData(tilePath, quality);
		} else {
			try {
				return Files.readAllBytes(Paths.get(tilePath));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}
	}
	
	public static byte[] getCompressData(String tilePath, float quality) {
		byte[] data = null;
		try {
			File imageFile = new File(tilePath);
			InputStream is = new FileInputStream(imageFile);
			ByteArrayOutputStream os = new ByteArrayOutputStream();

			// create a BufferedImage as the result of decoding the supplied InputStream
			BufferedImage image = ImageIO.read(is);

			// get all image writers for JPG format
			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

			if (!writers.hasNext())
				throw new IllegalStateException("No writers found");

			ImageWriter writer = (ImageWriter) writers.next();
			ImageOutputStream ios = ImageIO.createImageOutputStream(os);
			writer.setOutput(ios);

			ImageWriteParam param = writer.getDefaultWriteParam();

			// compress to a given quality
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(quality);

			// appends a complete image stream containing a single image and
		    //associated stream and image metadata and thumbnails to the output
			writer.write(null, new IIOImage(image, null, null), param);
			data = os.toByteArray();
			
			// close all streams
			is.close();
			os.close();
			ios.close();
			writer.dispose();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return data;
	}
	
	private static int swapRow(int zoomLevel,int y) {
		return ((int) (Math.pow(2, zoomLevel) - y) - 1);
	}
}
