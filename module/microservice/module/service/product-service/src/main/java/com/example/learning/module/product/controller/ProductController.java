package com.example.learning.module.product.controller;

import com.example.learning.module.product.dto.request.ProductRequest;
import com.example.learning.module.product.dto.response.ProductResponse;
import com.example.learning.module.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createProduct(@RequestBody @Valid ProductRequest request) {
        productService.createProduct(request);
    }

    @GetMapping
    public List<ProductResponse> getAllProduct() {
        return productService.getAllProduct();
    }
}
