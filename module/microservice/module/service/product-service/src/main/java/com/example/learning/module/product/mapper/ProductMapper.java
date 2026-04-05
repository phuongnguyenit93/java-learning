package com.example.learning.module.product.mapper;

import com.example.learning.module.product.dto.request.ProductRequest;
import com.example.learning.module.product.dto.response.ProductResponse;
import com.example.learning.module.product.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "id", ignore = true)
    Product toEntity(ProductRequest request);
    ProductResponse toResponse (Product product);
}
