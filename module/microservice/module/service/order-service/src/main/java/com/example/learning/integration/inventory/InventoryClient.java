package com.example.learning.integration.inventory;

import com.example.learning.integration.config.FeignClientInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "inventory" , url = "${integration.client.inventory}",configuration = FeignClientInterceptor.class)
public interface InventoryClient {

    @RequestMapping (method = RequestMethod.GET, value="/api/inventory/in-stock")
    boolean isInStock(@RequestParam String skuCode, @RequestParam Integer quantity);
}
