package com.example.learning.module.forkjoin.task;

import com.example.learning.shared.entity.Transaction;

import java.util.concurrent.RecursiveTask;
import java.util.List;
import java.util.ArrayList;

public class ForkJoinProcessor extends RecursiveTask<List<Long>> {
    private static final int THRESHOLD = 10_000;
    private final List<Transaction> transactions;
    private final int start;
    private final int end;

    public ForkJoinProcessor(List<Transaction> transactions, int start, int end) {
        this.transactions = transactions;
        this.start = start;
        this.end = end;
    }

    @Override
    protected List<Long> compute() {
        if ((end - start) <= THRESHOLD) {
            List<Long> invalidIds = new ArrayList<>();
            for (int i = start; i < end; i++) {
                if (!transactions.get(i).isValid()) {
                    invalidIds.add(transactions.get(i).getId());
                }
            }
            return invalidIds;
        }

        int mid = (start + end) / 2;
        ForkJoinProcessor leftTask = new ForkJoinProcessor(transactions, start, mid);
        ForkJoinProcessor rightTask = new ForkJoinProcessor(transactions, mid, end);

        // Kỹ thuật tối ưu: Fork nhánh trái, tính nhánh phải trực tiếp trên luồng hiện tại
        leftTask.fork();
        List<Long> rightResult = rightTask.compute();
        List<Long> leftResult = leftTask.join();

        // Gộp kết quả
        leftResult.addAll(rightResult);
        return leftResult;
    }
}
