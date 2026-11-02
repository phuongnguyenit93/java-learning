package com.example.learning.casestudy.application.group8.case5;

import com.example.learning.casestudy.port.group8.case5.Mail;

public record NotifyOrder(Mail mail) {
    public void completed(String user) { mail.notifyUser(user, "order completed"); }
}
