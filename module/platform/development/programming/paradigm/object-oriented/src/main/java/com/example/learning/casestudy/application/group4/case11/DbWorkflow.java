package com.example.learning.casestudy.application.group4.case11;

import com.example.learning.casestudy.application.group4.case11.Workflow;
import java.util.List;

public final class DbWorkflow extends Workflow {
    public DbWorkflow(List<String> steps){super(steps);}
    protected void persist(String input){steps.add("db:"+input);}
}
