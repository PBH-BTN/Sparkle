package com.ghostchu.btn.sparkle.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MsgUtil {
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    public static String getNowDateTimeString(){
        return dateTimeFormat.format(new Date());
    }
    public static String getNowDateString(){
        return dateFormat.format(new Date());
    }

    /**
     * Replace args in raw to args
     *
     * @param raw  text
     * @param args args
     * @return filled text
     */
    public static String fillArgs(String raw, String... args) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int start = 0;
        int argIndex = 0;

        while (start < raw.length()) {
            int placeholderIndex = raw.indexOf("{}", start);
            if (placeholderIndex == -1) {
                result.append(raw.substring(start));
                break;
            }
            result.append(raw, start, placeholderIndex);
            if (args != null && argIndex < args.length) {
                result.append(args[argIndex] != null ? args[argIndex] : "");
                argIndex++;
            } else {
                result.append("{}");
            }
            start = placeholderIndex + 2;
        }
        return result.toString();
    }
}
