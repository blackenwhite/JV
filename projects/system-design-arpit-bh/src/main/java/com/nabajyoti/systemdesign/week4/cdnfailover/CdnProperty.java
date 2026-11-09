package com.nabajyoti.systemdesign.week4.cdnfailover;

import java.util.LinkedHashMap;
import java.util.Map;
public record CdnProperty(String hostname, String originBaseUrl) {
    public static Map<String, CdnProperty> defaultProperties() {
        Map<String, CdnProperty> properties = new LinkedHashMap<>();
        CdnProperty cdnA = new CdnProperty("cdn-a.localhost", "http://localhost:8091");
        CdnProperty cdnB = new CdnProperty("cdn-b.localhost", "http://localhost:8092");
        properties.put(cdnA.hostname(), cdnA);
        properties.put(cdnB.hostname(), cdnB);
        return Map.copyOf(properties);
    }
    public static void main(String[] args) {
        for (CdnProperty property : defaultProperties().values()) {
            System.out.println(property.hostname() + " -> " + property.originBaseUrl());
        }
    }
}
