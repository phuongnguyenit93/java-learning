package com.example.learning.casestudy.repository.group12.case2;

import com.example.learning.casestudy.domain.group12.case2.Account;
import com.example.learning.casestudy.port.group12.case2.AccountRepository;

public class SpyRepository implements AccountRepository {
    public int saves;
    public int persistedBalance = -1;
    public void save(Account a) {
        saves++;
        persistedBalance = a.balance();
    }
}
