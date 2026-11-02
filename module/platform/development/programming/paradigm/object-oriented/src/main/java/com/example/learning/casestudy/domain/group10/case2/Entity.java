package com.example.learning.casestudy.domain.group10.case2;


import com.example.learning.casestudy.domain.group10.case2.EntityKey;


public final class Entity {

    final EntityKey key;

    public Long databaseId;

    public Entity(EntityKey key){
        this.key=key;
    }

    @Override public boolean equals(Object other){

        return this==other || other instanceof Entity e && key.equals(e.key);

    }

    @Override public int hashCode(){
        return key.hashCode();
    }

}
