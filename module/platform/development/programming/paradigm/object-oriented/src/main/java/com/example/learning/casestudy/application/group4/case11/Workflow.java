package com.example.learning.casestudy.application.group4.case11;

import java.util.List;

public abstract class Workflow {
    final List<String> steps;
    public Workflow(List<String> steps){this.steps=steps;}
    public final void execute(String input) {
        if (input == null || input.isBlank())
            throw new IllegalArgumentException("input");
        steps.add("validate");
        persist(input);
        steps.add("done");
    }
    protected abstract void persist(String input);
}
