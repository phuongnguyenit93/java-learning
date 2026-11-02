package com.example.learning.casestudy.application.group4.case11;

import com.example.learning.casestudy.application.group4.case11.Workflow;
import java.util.List;

public final class FileWorkflow extends Workflow {
    public FileWorkflow(List<String> steps){super(steps);}
    protected void persist(String input){steps.add("file:"+input);}
}
