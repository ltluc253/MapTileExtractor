package com.luc.map.crawler;

public class TileItem {
	int mZoomLevel;
	int mFromX;
	int mToX;
	int mFromY;
	int mToY;
	
	public TileItem(int mZoomLevel, int mFromX, int mToX, int mFromY, int mToY) {
		this.mZoomLevel = mZoomLevel;
		this.mFromX = mFromX;
		this.mToX = mToX;
		this.mFromY = mFromY;
		this.mToY = mToY;
	}
}
