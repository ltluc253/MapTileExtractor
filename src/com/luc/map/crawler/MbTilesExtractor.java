package com.luc.map.crawler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
	
	private static final String INSERT_CMD = "INSERT INTO tiles (zoom_level,tile_column,tile_row,tile_data,file_size) VALUES (?,?,?,?,?)";
	public static String EXTRACTED_PATH = "Extracted";
	public static boolean ONLY_CRAWLER = false;
	public static boolean COMPRESSION = true;
	
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
	         openDbConnection();
	         optimizeConnection();
	         setupConnection();

	         insertMetaData("minzoom", String.valueOf(fromZl));
	         insertMetaData("maxzoom", String.valueOf(toZl));
	         insertMetaData("bounds", mMapAreaItem.getBounds());
	      } catch ( Exception e ) {
	         System.err.println( e.getClass().getName() + ": " + e.getMessage() );
	      }
	}
	
	private void openDbConnection() {
		String dbFileName = EXTRACTED_PATH + File.separator + mMapAreaItem.getFilename() + ".mbtiles";
		try {
			Class.forName("org.sqlite.JDBC");
			mDbConnection = DriverManager.getConnection("jdbc:sqlite:" + dbFileName);
			mDbConnection.setAutoCommit(false);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void optimizeConnection() {
		//executeSqlCmd("PRAGMA synchronous=0");
		executeSqlCmd("PRAGMA locking_mode=EXCLUSIVE");
		executeSqlCmd("PRAGMA journal_mode=DELETE");
	}
	
	private void setupConnection() {
        executeSqlCmd("create table tiles (zoom_level integer,tile_column integer,tile_row integer,tile_data blob,file_size integer);");
        executeSqlCmd("create table metadata (name text, value text);");
        executeSqlCmd("CREATE TABLE grids (zoom_level integer, tile_column integer,tile_row integer, grid blob);");
        executeSqlCmd("CREATE TABLE grid_data (zoom_level integer, tile_column integer, tile_row integer, key_name text, key_json text);");
        executeSqlCmd("create unique index name on metadata (name);");
        executeSqlCmd("create unique index tile_index on tiles (zoom_level, tile_column, tile_row);");
	}
	
	private void compressionPrepare() {
		executeSqlCmd("CREATE TABLE if not exists images (tile_data blob,tile_id integer);");
		executeSqlCmd("CREATE TABLE if not exists map (zoom_level integer,tile_column integer,tile_row integer,tile_id integer);");
	}
	
	private void doCompress(int chunk) {
		int totalTiles = getTilesCount();
		int lastId = 0;
		int range = totalTiles / chunk + 1;
		List<Integer> ids = new ArrayList<>();
		List<ByteBuffer> files = new ArrayList<>();
		for (int i = 0; i < range; i++) {
			ids.clear();
			files.clear();
			List<Tile> tiles = getChunkTiles(chunk, i);
			for (Tile tile : tiles) {
				int fileIndex = files.indexOf(tile.tileImage);
				if (fileIndex >= 0) {
					insertMap(tile.zoomLv, tile.column, tile.row, ids.get(fileIndex));
				} else {
					lastId += 1;
					
					ids.add(lastId);
					files.add(tile.tileImage);
					
					insertImage(lastId, tile.tileImage.array());
					insertMap(tile.zoomLv, tile.column, tile.row, lastId);
				}
			}
			try {
				mDbConnection.commit();	
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
	}
	
	private void doCompressNew(int chunk) {
		int lastId = 0;
		List<Integer> ids = new ArrayList<>();
		List<ByteBuffer> files = new ArrayList<>();
		List<Integer> fSizes = getTileSizes();
		for (Integer size:fSizes) {
			List<Tile> tiles = null;
			int offset = 0;
			do {
				ids.clear();
				files.clear();
				long start = System.currentTimeMillis();
				tiles = getTilesBySize(size, offset, chunk);
				long duration = System.currentTimeMillis() - start;
				System.out.println("Duration: " + duration + ", " + tiles.size());
				if (tiles != null && !tiles.isEmpty()) {
					for (Tile tile : tiles) {
						int fileIndex = files.indexOf(tile.tileImage);
						if (fileIndex >= 0) {
							insertMap(tile.zoomLv, tile.column, tile.row, ids.get(fileIndex));
						} else {
							lastId += 1;
							
							ids.add(lastId);
							files.add(tile.tileImage);
							
							insertImage(lastId, tile.tileImage.array());
							insertMap(tile.zoomLv, tile.column, tile.row, lastId);
						}
					}
					try {
						mDbConnection.commit();	
					} catch (Exception e) {
						// TODO: handle exception
					}
				}
				offset += chunk;
			} while (tiles != null && tiles.size() >= chunk);
		}
	}
	
	private void finalizedCompress() {
		executeSqlCmd("drop table tiles;");
		executeSqlCmd("create view tiles as select map.zoom_level as zoom_level, map.tile_column as tile_column, map.tile_row as tile_row, images.tile_data as tile_data FROM map JOIN images on images.tile_id = map.tile_id;");
		executeSqlCmd("CREATE UNIQUE INDEX map_index on map (zoom_level, tile_column, tile_row);");
		executeSqlCmd("CREATE UNIQUE INDEX images_id on images (tile_id);");
	}
	
	private void optimizeDb() {
		executeSqlCmd("end transaction;");
		executeSqlCmd("ANALYZE;");
		executeSqlCmd("VACUUM;");
	}
	
	private int getTilesCount() {
		int tilesCount = 0;
		try {
			Statement statement = mDbConnection.createStatement();
			ResultSet rSet = statement.executeQuery("select count(zoom_level) from tiles");
			tilesCount = rSet.getInt(1);
			rSet.close();
			statement.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
		return tilesCount;		
	}
	
	private List<Integer> getTileSizes() {
		List<Integer> rSizes = new ArrayList<>();
		try {
			Statement statement = mDbConnection.createStatement();
			String cmd = String.format("select distinct(file_size) from tiles order by file_size asc");
			ResultSet rSet = statement.executeQuery(cmd);
			while (rSet.next()) {
				rSizes.add(rSet.getInt(1));				
			}
			rSet.close();
			statement.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return rSizes;
	}
	
	private List<Tile> getChunkTiles(int chunk, int index) {
		List<Tile> rTiles = new ArrayList<>();
		try {
			Statement statement = mDbConnection.createStatement();
			String cmd = String.format("select zoom_level, tile_column, tile_row, tile_data from tiles limit %d offset %d", chunk, chunk * index);
			ResultSet rSet = statement.executeQuery(cmd);
			while (rSet.next()) {
				rTiles.add(new Tile(rSet.getInt(1), rSet.getInt(2), rSet.getInt(3), rSet.getBytes(4)));				
			}
			rSet.close();
			statement.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return rTiles;
	}
	
	private List<Tile> getTilesBySize(int size, int offset, int limit) {
		List<Tile> rTiles = new ArrayList<>();
		try {
			Statement statement = mDbConnection.createStatement();
			String cmd = String.format("select zoom_level, tile_column, tile_row, tile_data from tiles where file_size=%d limit %d offset %d", size, limit, offset);
			ResultSet rSet = statement.executeQuery(cmd);
			while (rSet.next()) {
				rTiles.add(new Tile(rSet.getInt(1), rSet.getInt(2), rSet.getInt(3), rSet.getBytes(4)));				
			}
			rSet.close();
			statement.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return rTiles;
	}
	
	private void insertMap(int zoomLevel, int column, int row, int tileId) {
		try {
			Statement statement = mDbConnection.createStatement();
			String cmd = String.format("insert into map (zoom_level, tile_column, tile_row, tile_id) values (%d, %d, %d, %d)", zoomLevel, column, row, tileId);
			statement.executeUpdate(cmd);
			statement.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	private void insertImage(int tileId, byte[] tileData) {
		try {
			PreparedStatement statement = mDbConnection.prepareStatement("insert into images (tile_id, tile_data) values (?, ?)");
			statement.setInt(1, tileId);
			statement.setBytes(2, tileData);
			statement.executeUpdate();
			statement.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}		
	}
	
	private void insertMetaData(String key, String value) {
		try {
			Statement statement = mDbConnection.createStatement();
			String cmd = String.format("insert into metadata (name, value) values (\"%s\", \"%s\")", key, value);
			statement.executeUpdate(cmd);
			statement.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}	
	}
	
	private void executeSqlCmd(String cmd) {
		try {
			Statement mStatement = mDbConnection.createStatement();
	        mStatement.executeUpdate(cmd);
	        mDbConnection.commit();
		} catch (Exception e) {
			//e.printStackTrace();
		}
	}

	private void closeDb() {
		if (mDbConnection != null) {
			try {
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
				if (COMPRESSION) {
					System.out.println("Start Compression");
					compressionPrepare();
					doCompress(512);
					closeDb();
					openDbConnection();
					finalizedCompress();
					System.out.println("Compression Complete");
				}
				optimizeDb();
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
				byte[] tileData = getTileData(filePath, mCompressQuality);
				preparedStatement.setInt(1, zoomLv);
				preparedStatement.setInt(2, x);
				preparedStatement.setInt(3, swapRow(zoomLv, y));
				preparedStatement.setBytes(4, tileData);
				preparedStatement.setInt(5, tileData != null ? tileData.length : 0);
				preparedStatement.executeUpdate();
				preparedStatement.close();
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
	
	private class Tile {
		int zoomLv;
		int column;
		int row;
		ByteBuffer tileImage;
		public Tile(int zoomLv, int column, int row, byte[] tileImage) {
			this.zoomLv = zoomLv;
			this.column = column;
			this.row = row;
			this.tileImage = ByteBuffer.wrap(tileImage);
		}
		
		
	}
}
