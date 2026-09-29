package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/collection/list")
public class ListController {

    @GetMapping("/array-vs-linked")
    public Map<String, Object> arrayVsLinked() {
        List<String> arrayList = new ArrayList<>(List.of("A", "B", "C"));
        String arrayIndexOneBeforeInsert = arrayList.get(1);
        arrayList.add(1, "X");

        LinkedList<String> linkedList = new LinkedList<>(List.of("A", "B", "C"));
        linkedList.add(1, "X");
        linkedList.addFirst("HEAD");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("arrayListModel", "resizable array; indexed access is direct and middle insertion shifts later elements");
        result.put("arrayIndexOneBeforeInsert", arrayIndexOneBeforeInsert);
        result.put("arrayListAfterMiddleInsert", List.copyOf(arrayList));
        result.put("arrayElementShiftedToIndexTwo", arrayList.get(2));
        result.put("linkedListModel", "doubly linked nodes; endpoint changes relink nodes while indexed lookup traverses");
        result.put("linkedListAfterSameMiddleInsertAndHeadAdd", List.copyOf(linkedList));
        return result;
    }
}
