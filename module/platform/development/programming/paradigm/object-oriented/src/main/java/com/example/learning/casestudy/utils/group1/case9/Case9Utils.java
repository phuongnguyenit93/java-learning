package com.example.learning.casestudy.utils.group1.case9;

public final class Case9Utils {
    private Case9Utils() {}

    private static volatile Object keepAlive;

    public static long compute(int rounds) {
        long value = 0;
        for (int i = 0; i < rounds; i++) {
            value += (long)(i % 97) * (i % 31);
        }
        return value;
    }

    public static long allocate(int rounds) {
        long count = 0;
        for (int i = 0; i < rounds; i++) {
            byte[] bytes = new byte[1024];
            bytes[0] = (byte)i;
            keepAlive = bytes; // Tạo escape có quan sát được.
            count += bytes.length;
        }
        return count;
    }
}
