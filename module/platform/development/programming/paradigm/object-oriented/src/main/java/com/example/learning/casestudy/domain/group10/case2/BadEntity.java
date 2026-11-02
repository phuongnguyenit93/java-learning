package com.example.learning.casestudy.domain.group10.case2;


import java.util.Objects;


public final class BadEntity {

    public Long databaseId;

    @Override public boolean equals(Object other){

        return this==other || other instanceof BadEntity e
        && databaseId!=null && databaseId.equals(e.databaseId);

    }

    @Override public int hashCode(){
        return Objects.hashCode(databaseId);
    }

}
