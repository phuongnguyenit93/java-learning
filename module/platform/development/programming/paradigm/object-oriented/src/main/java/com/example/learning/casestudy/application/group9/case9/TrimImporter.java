package com.example.learning.casestudy.application.group9.case9;

import com.example.learning.casestudy.application.group9.case9.ImportTemplate;
import com.example.learning.casestudy.repository.group9.case9.Store;

public class TrimImporter extends ImportTemplate {
    public TrimImporter(Store store) { super(store); }
    @Override protected String transform(String raw) { return raw.trim(); }
}
