package com.example.learning.casestudy.application.group13.case6;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BoundedCache {
    public final Map<String, String> entries;
    public BoundedCache(int max){
        if (max<1)throw new IllegalArgumentException("max");
        entries = new LinkedHashMap<>(16, 0.75f, true){
            @Override protected boolean removeEldestEntry(
            Map.Entry<String, String> eldest){
                return size()>max;
            }
        };
    }
    public void put(String id, String result){
        entries.put(id, result);
    }
    public String get(String id){
        return entries.get(id);
    }
    public int size(){
        return entries.size();
    }
}
