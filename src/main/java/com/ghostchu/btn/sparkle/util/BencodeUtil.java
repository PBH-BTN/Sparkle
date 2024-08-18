package com.ghostchu.btn.sparkle.util;

import com.dampcake.bencode.Bencode;

import java.nio.charset.StandardCharsets;

public class BencodeUtil {
    public static final Bencode INSTANCE = new Bencode(StandardCharsets.ISO_8859_1);
    private static final Bencode UTF8 = new Bencode(StandardCharsets.UTF_8);

}
