package com.example.talgat.distancecounter.model;

public class Request {

    private String customerId;
    private String address;
    private String latitude;
    private String longitude;

    public Request() {
    }

    public Request(String customerId, String address, String latitude, String longitude) {
        this.customerId = customerId;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }


    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }
}
