package com.example.learning.casestudy.adapter.group6.case13;

import com.example.learning.casestudy.adapter.group6.case13.LibrarySession;
import com.example.learning.casestudy.adapter.group6.case13.LibraryUtility;

public class LibraryAdapter {
    private final LibraryUtility utility;
    public LibraryAdapter(LibraryUtility utility) { this.utility = utility; }
    public String execute(LibrarySession session, String input) {
        return session.query(utility.normalize(input));
    }
}
