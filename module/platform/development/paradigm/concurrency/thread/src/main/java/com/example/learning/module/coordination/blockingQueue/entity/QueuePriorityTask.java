package com.example.learning.module.coordination.blockingQueue.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class QueuePriorityTask implements Comparable<QueuePriorityTask> {
    private String content;
    private int priority;

    @Override
    public int compareTo(QueuePriorityTask task) {
        return Integer.compare(this.priority, task.priority);
    }
}
