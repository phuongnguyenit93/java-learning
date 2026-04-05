package com.example.learning.module.product.service;

import com.example.learning.module.product.dto.request.ProductRequest;
import com.example.learning.module.product.dto.response.ProductResponse;
import com.example.learning.module.product.mapper.ProductMapper;
import com.example.learning.module.product.model.Product;
import com.example.learning.module.product.repository.ProductRepository;
import com.mongodb.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public void createProduct(ProductRequest productRequest) {
        Product product = productMapper.toEntity(productRequest);

        try {
            productRepository.save(product);
            log.info("product create successfully");
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("Tên sản phẩm đã tồn tại!");
        }

    }

    public List<ProductResponse> getAllProduct() {
        List <Product> productList = productRepository.findAll();
        return productList.stream().map(productMapper::toResponse).toList();
    }
}
