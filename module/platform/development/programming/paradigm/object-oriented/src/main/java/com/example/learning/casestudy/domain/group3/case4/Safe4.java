package com.example.learning.casestudy.domain.group3.case4;

public class Safe4 {
    private final byte[] data;
    public Safe4(byte[] value) {
        if (value == null || value.length == 0)throw new IllegalArgumentException();
        data = value.clone();
    }
    public byte[] snapshot() {
        return data.clone();
    }
}
