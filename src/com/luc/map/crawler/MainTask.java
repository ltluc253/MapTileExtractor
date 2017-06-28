package com.luc.map.crawler;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class MapTileExtractor {

	public static void main(String[] args) {
		//Read input args
		// TODO Auto-generated method stub		
		int fromZl = -1;
		int toZl  = -1;
		float compresFactor = 1.0f;
		String city = null;
		String country = null;
		int threadCount = 100;
		String tileFilePath = "tile_list.json";
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
			if ("-city".equals(args[i])) {
				city = args[i+1];
				i += 1;
			}
			if ("-country".equals(args[i])) {
				country = args[i+1];
				i += 1;
			}
			if ("-compressFactor".equals(args[i])) {
				compresFactor = Float.parseFloat(args[i+1]);
				i += 1;
			}
			if ("-tileListPath".equals(args[i])) {
				tileFilePath = args[i+1];
				i += 1;
			}
			if ("-storageTilePath".equals(args[i])) {
				DownloadTask.STORAGE_PATH = args[i+1];
				i += 1;
			}
			if ("-extractedPath".equals(args[i])) {
				MapAreaExtractor.EXTRACTED_PATH = args[i+1];
				i += 1;
			}
		}
		if (fromZl < 1 || toZl < 1 || fromZl > 22 || toZl > 22) {
			System.out.println("Invalid zoom level");
			return;
		}
		if (compresFactor < 0.1 || compresFactor > 1) {
			System.out.println("Invalid compress factor");
			return;
		}
		
		//Read Maptile list
		MapFileItem mapFileItem = readMapAreaFromFiles(tileFilePath);
		List<MapAreaItem> extractCities = getRetriveArea(mapFileItem.mCities, country, city);
		if (extractCities.isEmpty()) {
			System.out.println("No city data found");
		} else {
			for (MapAreaItem item : extractCities) {
				MapAreaExtractor extractor = new MapAreaExtractor(item, fromZl, toZl, threadCount, compresFactor);
				extractor.startExtract();
			}
		}
		
	}
	
	private static List<MapAreaItem> getRetriveArea (List<MapAreaItem> allCities, String country, String city) {
		List<MapAreaItem> results = new ArrayList<>();
		for (MapAreaItem item : allCities) {
			if ((city != null && city.equalsIgnoreCase(item.getTitle())) ||
					(country != null && item.getName().contains(country.toLowerCase()))) {
				results.add(item);
			}
		}
		return results;
	}
	
	private static MapFileItem readMapAreaFromFiles(String filePath) {
		try {
			String content = new String(Files.readAllBytes(Paths.get(filePath)));
			Gson gson = new Gson();
			return gson.fromJson(content,  MapFileItem.class);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static class MapFileItem {
		@SerializedName("world")
		List<MapAreaItem> mWorld;
		@SerializedName("city")
		List<MapAreaItem> mCities;
		@SerializedName("country")
		List<MapAreaItem> mCountries;
	}

}
