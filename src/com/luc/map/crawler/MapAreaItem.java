package com.luc.map.crawler;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Created by Admin on 28/06/2017.
 */

public class MapAreaItem {


    @SerializedName("filename")
    private String mFilename;
    @SerializedName("title")
    private String mTitle;
    @SerializedName("link")
    private String mLink;
    @SerializedName("size")
    private String mSize;
    @SerializedName("name")
    private String mName;
    @SerializedName("date")
    private String mDate;
    @SerializedName("size_bytes")
    private long mSizeBytes;
    @SerializedName("bbox")
    private List<String> mBbox;

    public String getFilename() {
        return mFilename;
    }

    public void setFilename(String mFilename) {
        this.mFilename = mFilename;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    public String getLink() {
        return mLink;
    }

    public void setLink(String mLink) {
        this.mLink = mLink;
    }

    public String getSize() {
        return mSize;
    }

    public void setSize(String mSize) {
        this.mSize = mSize;
    }

    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public String getDate() {
        return mDate;
    }

    public void setDate(String mDate) {
        this.mDate = mDate;
    }

    public long getSizeBytes() {
        return mSizeBytes;
    }

    public void setSizeBytes(long mSizeBytes) {
        this.mSizeBytes = mSizeBytes;
    }

    public List<String> getBbox() {
        return mBbox;
    }

    public void setBbox(List<String> mBbox) {
        this.mBbox = mBbox;
    }
    
    public String getBounds() {
    	String bounds = "";
    	for (int i = 0; i < mBbox.size(); i++) {
    		bounds += mBbox.get(i) + (i < mBbox.size() - 1 ? "," : "");
    	}
    	return bounds;
    }
}
