package Model.Data;

import java.io.Serializable;

public class Location implements Serializable {
    public double longitude;
    public double latitude;

    public Location() {
    }

    public Location(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
