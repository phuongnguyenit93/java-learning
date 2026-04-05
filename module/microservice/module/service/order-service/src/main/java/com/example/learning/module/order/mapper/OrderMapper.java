package com.example.learning.module.order.mapper;
import com.example.learning.module.order.dto.OrderRequest;
import com.example.learning.module.order.dto.OrderResponse;
import com.example.learning.module.order.model.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    Order toEntity(OrderRequest request);
    OrderResponse toResponse (Order order);
}
