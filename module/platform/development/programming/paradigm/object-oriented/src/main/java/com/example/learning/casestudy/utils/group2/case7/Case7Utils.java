package com.example.learning.casestudy.utils.group2.case7;

import com.example.learning.casestudy.model.group2.case7.Customer7;

public final class Case7Utils {
    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static Customer7 withLabel(String id, String label) {
        return new Customer7(id, label, null);
    }
    public static Customer7 withRank(String id, Integer rank) {
        return new Customer7(id, null, rank);
    }
}
