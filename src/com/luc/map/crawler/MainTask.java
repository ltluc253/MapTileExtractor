package com.luc.map.crawler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class MainTask {

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
		String bBox = null;
		String outputName = null;
		for (int i = 0; i < args.length; i++) {
			if ("--help".equals(args[i])) {
				printHelp();
				return;
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
				MbTilesExtractor.EXTRACTED_PATH = args[i+1];
				i += 1;
			}
			if ("-o".equals(args[i])) {
				outputName = args[i + 1];
				i += 1;
			}
			if ("-bBox".equals(args[i])) {
				bBox = args[i + 1];
				i += 1;
			}
			if ("-onlyCrawler".equals(args[i])) {
				MbTilesExtractor.ONLY_CRAWLER = true;
			}
			if ("-noCompression".equals(args[i])) {
				MbTilesExtractor.COMPRESSION = false;
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
		
		if (outputName != null && bBox != null) {
			String[] bounds = bBox.split(" ");
			if (bounds.length > 3) {
				MapAreaItem mMapAreaItem = new MapAreaItem();
				mMapAreaItem.setBbox(Arrays.asList(bounds));
				mMapAreaItem.setFilename(outputName);
				mMapAreaItem.setTitle(outputName);
				MbTilesExtractor extractor = new MbTilesExtractor(mMapAreaItem, fromZl, toZl, threadCount, compresFactor);
				extractor.startExtract();
			} else {
				System.out.println("Invalid bBox");
			}
		} else {
			//Read Maptile list
			MapFileItem mapFileItem = readMapAreaFromFiles(tileFilePath);
			List<MapAreaItem> extractCities = getRetriveArea(mapFileItem.mCities, country, city);
			if (extractCities.isEmpty()) {
				System.out.println("No city data found");
			} else {
				for (MapAreaItem item : extractCities) {
					MbTilesExtractor extractor = new MbTilesExtractor(item, fromZl, toZl, threadCount, compresFactor);
					extractor.startExtract();
				}
			}
		}
	}
	
	private static void printHelp() {
		System.out.println("Input params: \n"
				+ "    -zlRange : zoom level range, it is two number with lower number is placed first\n"
				+ "    -threads: num of threads will run for this task, default value is 100\n"
				+ "    -endpoint: map tile end point \n"
				+ "    -country: search key for country \n"
				+ "    -city: search key for city\n"
				+ "    -compressFactor: 0~1, this param only support for jpg tile => choose it package process will run slowly\n"
				+ "    -tileListPath: path of tile list https://openmaptiles.com/downloads/list.json \n"
				+ "    -storageTilePath: storage path of crawler map tiles, default value is MapData\n"
				+ "    -extractedPath: storage path of extracted mbtiles file, default value is Extracted\n"
				+ "    -o: output file name it does not include mbtiles extension \n"
				+ "    -bBox: the box of region which is extracted, the format is [leftLng bottomLat rightLng topLat] \n"
				+ "    -onlyCrawler: only crawler map data \n"
				+ "    -noCompression: no run compression, help run faster but file size will be larger \n"
				+ "Note: input -o and -bBox will extract custom area, the params -country, -city, -tileListPath won't be effect");
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
