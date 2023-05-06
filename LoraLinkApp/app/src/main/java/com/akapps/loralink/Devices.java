package com.akapps.loralink;

import java.io.Serializable;

public class Devices implements Serializable{

    private String name;
    private String address;

    public Devices(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }
}
