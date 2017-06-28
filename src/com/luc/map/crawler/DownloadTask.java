package com.luc.map.crawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DownloadTask implements Runnable{
	
	public static int sFormatOrder = 0;
	public static String BASE_URL = "http://mt3.google.com/vt/v=w2.97&x=%d&y=%d&z=%d";
	public static String STORAGE_PATH = "MapData";
	public static int sSuccessCount = 0;
	public static int sFailCount = 0;
	public static int sOpenningStream = 0;
	public static int sDownloadingThread = 0;
	
	private List<TileItem> mPendingTiles = new ArrayList<>();
	private int mThreadIndex;
	private int mThreadCount;
	private OnTileAvailable mOnTileAvailable;
	
	public DownloadTask(List<TileItem> mPendingTiles, int threadIndex, int threadCount) {
		this.mPendingTiles = mPendingTiles;
		mThreadIndex = threadIndex;
		mThreadCount = threadCount;
	}

	public void setmOnTileAvailable(OnTileAvailable mOnTileAvailable) {
		this.mOnTileAvailable = mOnTileAvailable;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		for (TileItem tileItem : mPendingTiles) {
			int x = tileItem.mFromX;
			int y = tileItem.mFromY;
			int diffY = tileItem.mToY - tileItem.mFromY + 1;
			int index = 0;
			while (x <= tileItem.mToX) {
				int orderDownload = index * mThreadCount + mThreadIndex;
				x = tileItem.mFromX + orderDownload / diffY;
				y = tileItem.mFromY + orderDownload % diffY;
				if (x <= tileItem.mToX && y <= tileItem.mToY) {
					downloadImage(tileItem.mZoomLevel, x, y);
				}
				index++;
			}
		}
	}
	
	private void downloadImage(int zoomLevel, int x, int y) {
		String fileFolder = STORAGE_PATH + File.separator + zoomLevel + File.separator + x;
		File storageFolder = new File(fileFolder);
		if (!storageFolder.exists()) {
			storageFolder.mkdirs();
		}
		String destinationFilePath = fileFolder + File.separator + y + (BASE_URL.lastIndexOf(".") > 0 ? BASE_URL.substring(BASE_URL.lastIndexOf(".")) : ".png");
		if ((new File(destinationFilePath)).exists()) {
			sSuccessCount += 1;
			if (mOnTileAvailable != null) {
				mOnTileAvailable.onTileFileAvailable(zoomLevel, x, y, destinationFilePath);
			}
			return;
		} else {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		sDownloadingThread += 1;
		String imageUrl;
		if (sFormatOrder == 1) {
			imageUrl = String.format(BASE_URL, x, y, zoomLevel);
		} else {
			imageUrl = String.format(BASE_URL, zoomLevel, x, y);
		}
		InputStream is = null;
		OutputStream os = null;
		try {
			URL url = new URL(imageUrl);
			is = url.openStream();
			sOpenningStream += 1;
			os = new FileOutputStream(destinationFilePath);
	
			byte[] b = new byte[2048];
			int length;
	
			while ((length = is.read(b)) != -1) {
				os.write(b, 0, length);
			}
	
			is.close();
			os.close();
			sSuccessCount += 1;
			if (mOnTileAvailable != null) {
				mOnTileAvailable.onTileFileAvailable(zoomLevel, x, y, destinationFilePath);
			}
		} catch (IOException ioException) {
			sFailCount += 1;
			System.out.println("Fail to download file: " + imageUrl + " ------ " + sFailCount);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			sOpenningStream -= 1;
			sDownloadingThread -= 1;
		}
	}
	
	interface OnTileAvailable {
		void onTileFileAvailable(int zoomLv, int x, int y, String filePath);
	}
}
