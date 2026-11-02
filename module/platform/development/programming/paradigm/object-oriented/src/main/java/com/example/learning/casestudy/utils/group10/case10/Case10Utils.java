package com.example.learning.casestudy.utils.group10.case10;



public final class Case10Utils {

    private Case10Utils() {

    }


    public static void check(boolean ok){
        if(!ok)throw new AssertionError();
    }


    public static void rejects(Runnable action){

        try{
            action.run();
            throw new AssertionError("invalid transition allowed");
        }

        catch(IllegalStateException expected){
        }

    }

}
