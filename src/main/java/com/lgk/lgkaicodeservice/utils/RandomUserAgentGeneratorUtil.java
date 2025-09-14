package com.lgk.lgkaicodeservice.utils;

import java.util.Random;

public class RandomUserAgentGeneratorUtil {

    private static final Random RANDOM = new Random();

    private static final String[] OS = {
        "Windows NT 10.0; Win64; x64",
        "Windows NT 6.1; Win64; x64",
        "Macintosh; Intel Mac OS X 13_2",
        "Macintosh; Intel Mac OS X 10_15_7",
        "X11; Linux x86_64"
    };

    private static final String[] BROWSERS = {"Chrome", "Firefox", "Edg"};

    public static String generate() {
        String os = OS[RANDOM.nextInt(OS.length)];
        String browser = BROWSERS[RANDOM.nextInt(BROWSERS.length)];

        switch (browser) {
            case "Chrome":
                return String.format(
                    "Mozilla/5.0 (%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%d.0.%d.%d Safari/537.36",
                    os,
                    100 + RANDOM.nextInt(30),   // 100 - 129
                    4000 + RANDOM.nextInt(1000), // build
                    RANDOM.nextInt(200)         // patch
                );
            case "Firefox":
                return String.format(
                    "Mozilla/5.0 (%s; rv:%d.0) Gecko/20100101 Firefox/%d.0",
                    os,
                    90 + RANDOM.nextInt(30),  // 90 - 119
                    90 + RANDOM.nextInt(30)
                );
            case "Edg":
                return String.format(
                    "Mozilla/5.0 (%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%d.0.%d.%d Safari/537.36 Edg/%d.0.%d.%d",
                    os,
                    100 + RANDOM.nextInt(30),
                    4000 + RANDOM.nextInt(1000),
                    RANDOM.nextInt(200),
                    100 + RANDOM.nextInt(30),
                    4000 + RANDOM.nextInt(1000),
                    RANDOM.nextInt(200)
                );
            default:
                return "Mozilla/5.0";
        }
    }
}