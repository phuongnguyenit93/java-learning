package com.example.learning.casestudy.domain.group10.case2;


import java.util.Objects;

import java.util.UUID;


public record EntityKey(UUID value){

    public EntityKey {
        Objects.requireNonNull(value);
    }

}
