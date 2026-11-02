package com.example.learning.casestudy.utils.group10.case3;


import java.util.Locale;


public final class Case3Utils {

    private Case3Utils() {

    }


    public static void check(boolean ok){
        if(!ok)throw new AssertionError();
    }


    public static String normalize(String text){

        if(text==null||text.isBlank())throw new IllegalArgumentException();

        return text.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);

    }

}
