package com.example.learning.controller;

import com.example.learning.UserDetailEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
@RequestMapping("objectmapper")
@Tag(name = "Object Mapper")
public class ObjectMapperController {
    @Autowired
    ObjectMapper objectMapper;

    @GetMapping("/mapToString")
    public String convertMapToString() {
        String result = "";
        try {
            UserDetailEntity user = new UserDetailEntity();
            result = objectMapper.writeValueAsString(user);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return result;
    }

    @GetMapping("/stringToMap")
    public HashMap convertMapToString2() {
        String objectStr = "";
        HashMap result = new HashMap();
        try {
            objectStr = "{\"id\":1,\"username\":\"mysql\",\"password\":\"mysql\"}";
            result = objectMapper.readValue(objectStr,HashMap.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return result;
    }
}
