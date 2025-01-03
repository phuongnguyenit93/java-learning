package com.example.learning.controller;

import com.example.learning.UserDetailEntity;
import com.example.learning.UserEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/modelmapper")
@Tag(name = "Model Mapper")
public class ModelMapperController {

    @Autowired
    ModelMapper modelMapper;

    @GetMapping("/example")
    public UserEntity modelExample(){
        UserDetailEntity userDetail = new UserDetailEntity();
        UserEntity user = modelMapper.map(userDetail,UserEntity.class);
        return user;
    }
}
