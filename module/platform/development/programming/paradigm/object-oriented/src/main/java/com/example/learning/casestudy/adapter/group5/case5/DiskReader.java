package com.example.learning.casestudy.adapter.group5.case5;

import com.example.learning.casestudy.port.group5.case5.Reader;
import java.io.FileNotFoundException;

public final class DiskReader extends Reader {
    @Override public String read() throws FileNotFoundException {
        return "found";
    }
}
