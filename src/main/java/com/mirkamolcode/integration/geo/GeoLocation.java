package com.mirkamolcode.integration.geo;

public record GeoLocation(String city, String country) {
    public static final GeoLocation UNKNOWN = new GeoLocation("Unknown", "Unknown");
    public static final GeoLocation LOCALHOST = new GeoLocation("Localhost", "Local");
    public static final GeoLocation LOCAL_NETWORK = new GeoLocation("Local Network", "Local");

    public boolean isKnown() {
        return city != null && !city.isBlank() && !"Unknown".equalsIgnoreCase(city);
    }
}
