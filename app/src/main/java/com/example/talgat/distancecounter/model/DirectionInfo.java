package com.example.talgat.distancecounter.model;

import com.google.maps.model.LatLng;

import java.util.List;

public class DirectionInfo {
    private String customerId;
    private List<com.google.maps.model.LatLng> path;
    private String time;
    private long actualDistance;

    public DirectionInfo(String customerId, List<LatLng> path, String time, long actualDistance) {
        this.customerId = customerId;
        this.path = path;
        this.time = time;
        this.actualDistance = actualDistance;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public List<LatLng> getPath() {
        return path;
    }

    public void setPath(List<LatLng> path) {
        this.path = path;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public long getActualDistance() {
        return actualDistance;
    }

    public void setActualDistance(long actualDistance) {
        this.actualDistance = actualDistance;
    }
}
