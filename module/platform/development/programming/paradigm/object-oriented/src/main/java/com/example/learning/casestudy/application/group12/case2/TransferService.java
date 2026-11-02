package com.example.learning.casestudy.application.group12.case2;

import com.example.learning.casestudy.domain.group12.case2.Account;
import com.example.learning.casestudy.port.group12.case2.AccountRepository;

public class TransferService {
    private final AccountRepository repository;
    public TransferService(AccountRepository repository) {
        this.repository = repository;
    }
    public void withdraw(Account account, int cents) {
        account.debit(cents);
        repository.save(account);
    }
}
