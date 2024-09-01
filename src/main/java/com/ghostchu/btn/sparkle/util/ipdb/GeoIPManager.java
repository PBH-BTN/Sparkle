package com.ghostchu.btn.sparkle.util.ipdb;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;

@Component
public class GeoIPManager {
    private IPDB ipdb;

    public GeoIPManager() throws IOException {
        this.ipdb = new IPDB(true, "Sparkle/1.0");
    }

    public IPGeoData geoData(InetAddress inet) {
        return this.ipdb.query(inet);
    }
}
