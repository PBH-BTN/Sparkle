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
}
