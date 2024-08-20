package com.ghostchu.btn.sparkle.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MsgUtil {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static String getNowDateTimeString(){
        return dateFormat.format(new Date());
    }
}
