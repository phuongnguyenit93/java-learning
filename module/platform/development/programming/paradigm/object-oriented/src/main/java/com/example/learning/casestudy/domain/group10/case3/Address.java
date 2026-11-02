package com.example.learning.casestudy.domain.group10.case3;


import com.example.learning.casestudy.utils.group10.case3.Case3Utils;


public record Address(String street,String city){

    public Address {

        street=Case3Utils.normalize(street);

        city=Case3Utils.normalize(city);

    }


}
