package com.ghostchu.btn.sparkle.util;

import java.util.concurrent.Semaphore;

public class DatabaseCare {
    public static final Semaphore generateParallel = new Semaphore(3);
}
