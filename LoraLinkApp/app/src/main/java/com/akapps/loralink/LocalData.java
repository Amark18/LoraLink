package com.akapps.loralink;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Random;

public class LocalData {

    private static final String PREFS_NAME = "lora_link_pref";
    private static final String SENDER_ID_KEY = "sender_id_key";
    private static final String DEVICE_NAME_KEY = "device_name_key";

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void setSenderID(Context context, int senderID) {
        SharedPreferences preferences = getSharedPreferences(context);
        if (preferences.getInt(SENDER_ID_KEY, 0) == 0) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(SENDER_ID_KEY, senderID);
            editor.apply();
        }
    }

    public static int getSenderID(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        int senderID = preferences.getInt(SENDER_ID_KEY, 0);
        if (senderID == 0) {
            senderID = new Random().nextInt(Integer.MAX_VALUE - 1) + 1;
            setSenderID(context, senderID);
        }
        return senderID;
    }

    public static void setDeviceName(Context context, String deviceName) {
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(DEVICE_NAME_KEY, deviceName);
        editor.apply();
    }

    public static String getDeviceName(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String deviceName = preferences.getString(DEVICE_NAME_KEY, "");
        return deviceName;
    }
}
