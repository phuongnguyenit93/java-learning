package com.example.learning.casestudy.repository.group9.case9;

import java.util.ArrayList;
import java.util.List;

public class Store {
    public final List<String> saved = new ArrayList<>();
    public void save(String data) { saved.add(data); }
}
