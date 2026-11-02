package com.example.learning.casestudy.model.group3.case11;

import java.util.Date;

public class Safe11 {
    private final Date date;
    public Safe11(Date source) {
        date = new Date(source.getTime());
    }
    public Date date() {
        return new Date(date.getTime());
    }
}
