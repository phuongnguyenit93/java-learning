package com.example.learning.controller;

import com.example.learning.entity.table.UserTableEntity;
import com.example.learning.repository.table.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping
@Tag(name = "JPA")
public class JPAController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ObjectMapper objectMapper;

    @GetMapping("/getUser")
    public UserTableEntity getUser() {
        List<UserTableEntity> users = userRepository.findAll();
        return users.get(0);
    }

    @GetMapping("/stringMapper")
    public String stringMapper() throws JsonProcessingException {
        HashMap object = new HashMap();
        object.put("test1","abc");
        object.put("test2","cde");
        object.put("test3","fgh");
        String result = objectMapper.writeValueAsString(object);
        return result;
    }

    @GetMapping("/objectMapper")
    public HashMap object() throws JsonProcessingException {
        String objectStr = "{\"id\":1,\"username\":\"mysql\",\"password\":\"mysql\"}";
        HashMap result = objectMapper.readValue(objectStr,HashMap.class);
        return result;
    }
}
