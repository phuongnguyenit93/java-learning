package com.example.learning.casestudy.port.group12.case3;

import com.example.learning.casestudy.domain.group12.case3.Shipment;

public interface ShippingPrice {
    int price(Shipment shipment);
}
