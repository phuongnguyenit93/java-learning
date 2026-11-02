package com.example.learning.casestudy.utils.group10.case9;



public final class Case9Utils {

    private Case9Utils() {

    }


    public static void check(boolean ok){
        if(!ok)throw new AssertionError();
    }


    public static void reject(Runnable r){

        try{
            r.run();
            throw new AssertionError("cancel accepted");
        }

        catch(IllegalStateException expected){
        }

    }

}
