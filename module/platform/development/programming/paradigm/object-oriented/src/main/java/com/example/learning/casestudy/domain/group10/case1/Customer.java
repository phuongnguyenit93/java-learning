package com.example.learning.casestudy.domain.group10.case1;


import java.util.Objects;


public final class Customer {

    private final String customerId;

    private String name;

    public Customer(String id,String name) {

        if(id==null || id.isBlank()) throw new IllegalArgumentException("id");

        customerId=id;
        this.name=Objects.requireNonNull(name);

    }

    public void rename(String value){
        name=Objects.requireNonNull(value);
    }

    @Override public boolean equals(Object other){

        return this==other || other instanceof Customer c
        && customerId.equals(c.customerId);

    }

    @Override public int hashCode(){
        return customerId.hashCode();
    }

}
