package com.luc.map.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Crawler {

	public static void main(String[] args) {
		// TODO Auto-generated method stub		
		int fromZl = -1;
		int toZl  = -1;
		double north = 90;
		double east = 180;
		double south = -90;
		double west = -180;
		int threadCount = 100;
		for (int i = 0; i < args.length; i++) {
			if ("-zl".equals(args[i]) && (fromZl == -1 && toZl == -1)) {
				fromZl = Integer.parseInt(args[i+1]);
				toZl = fromZl;
				i += 1;
			}
			if ("-zlRange".equals(args[i])) {
				fromZl = Integer.parseInt(args[i+1]);
				toZl = Integer.parseInt(args[i+2]);
				i += 2;
			}
			if ("-bounds".equals(args[i])) {
				north = Double.parseDouble(args[i+1]);
				east = Double.parseDouble(args[i+2]);
				south = Double.parseDouble(args[i+3]);
				west = Double.parseDouble(args[i+4]);
				i += 4;
			}
			if ("-threads".equals(args[i])) {
				threadCount = Integer.parseInt(args[i+1]);
				threadCount = threadCount < 1 ? 50 : threadCount;
				i += 1;
			}
			if ("-endpoint".equals(args[i])) {
				DownloadTask.BASE_URL = args[i + 1];
				i += 1;
			}
			if ("-formatOrder".equals(args[i])) {
				DownloadTask.sFormatOrder =  Integer.parseInt(args[i+1]);
				i += 1;
			}
		}
		if (fromZl < 1 || toZl < 1 || fromZl > 22 || toZl > 22) {
			System.out.println("Invalid zoom level");
			return;
		}
		if (north < -90 || north > 90 || south < -90 || south > 90 ||
				east < -180 || east > 180 || west < -180 || west > 180) {
			System.out.println("Invalid latlng bounds (top and bottom is from -90 to 90 | left and right from -180 to 180)");
			return;
		}
		System.out.println("Start fetch map tile from: " + DownloadTask.BASE_URL + "\n" +
				"Bounds: " + west +" - " + north + " - " + east + " - " + south + "\n" +
				"Zoom Level: " + fromZl + " - " + toZl);
		long startDownloadTime = System.currentTimeMillis();
		int totalFile = 0;
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
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		for (int i = 0; i < threadCount; i++) {
			executor.execute(new DownloadTask(cordinates, i, threadCount));
		}
        executor.shutdown();
        int lastProgress = 0;
        while (!executor.isTerminated()) {
        	int progress = (DownloadTask.sSuccessCount + DownloadTask.sFailCount) * 100 / totalFile;
        	if (progress != lastProgress) {
        		lastProgress = progress;
        		long pastTime = System.currentTimeMillis() - startDownloadTime;
        		long remainTimeMs = (100 - lastProgress) * (pastTime / lastProgress);
        		System.out.println("Download progress: " + lastProgress + " --- Remain Time: " + formatTime(remainTimeMs / 1000) + " --- Past Time: " + formatTime(pastTime / 1000));
        	}
        	try {
				Thread.sleep(100);
				//System.out.println("Active Thread: " + Thread.activeCount());
				//System.out.println("Opening Stream: "  + DownloadTask.sOpenningStream + "/" + DownloadTask.sDownloadingThread);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        long totalDownloadTime = (System.currentTimeMillis() - startDownloadTime) / 1000;
        System.out.println("Download Time " + formatTime(totalDownloadTime));

	}
	
	private static String formatTime(long timeInSecond) {
        int hour = (int) (timeInSecond / 3600);
        int minute = (int) ((timeInSecond % 3600) / 60);
        int second = (int) (timeInSecond - hour * 3600 - minute * 60);
        return String.format("%2d:%2d:%2d", hour, minute, second);
	}

}
