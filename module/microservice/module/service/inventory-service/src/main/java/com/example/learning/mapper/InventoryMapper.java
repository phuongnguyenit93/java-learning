package com.example.learning.mapper;

import com.example.learning.dto.InventoryRequest;
import com.example.learning.model.Inventory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InventoryMapper {
    Inventory toEntity(InventoryRequest inventoryRequest);
}
