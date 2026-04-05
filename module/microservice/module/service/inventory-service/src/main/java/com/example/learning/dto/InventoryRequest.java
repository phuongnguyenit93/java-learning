package com.example.learning.dto;

public record InventoryRequest (
   String skuCode,
   Integer quantity
){ }
