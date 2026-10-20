package com.example.learning.oop.practice.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request-local pairwise-account exercise; protected by one ledger lock, not durable banking. */
public final class BankLedger {
    public record Seed(String id, String owner, long openingBalanceVnd) {}
    public record AccountView(String id, String owner, long balanceVnd) {}
    private static final class Account {
        private final String id;
        private final String owner;
        private long balance;
        private Account(Seed seed) {
            if (seed == null || seed.id() == null || !seed.id().matches("[A-Z0-9-]{2,24}")
                    || seed.owner() == null || seed.owner().isBlank() || seed.owner().length() > 80
                    || seed.openingBalanceVnd() < 0) {
                throw new IllegalArgumentException("account id, owner and nonnegative balance required");
            }
            id = seed.id();
            owner = seed.owner().strip();
            balance = seed.openingBalanceVnd();
        }
        AccountView view() { return new AccountView(id, owner, balance); }
    }

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    public BankLedger(List<Seed> seeds) {
        if (seeds == null || seeds.isEmpty() || seeds.size() > 10) {
            throw new IllegalArgumentException("accounts must contain 1..10 entries");
        }
        for (Seed seed : seeds) {
            Account a = new Account(seed);
            if (accounts.putIfAbsent(a.id, a) != null) {
                throw new IllegalArgumentException("duplicate account ID");
            }
        }
    }

    public synchronized List<AccountView> snapshot() {
        return accounts.values().stream().map(Account::view).toList();
    }

    public synchronized void deposit(String id, long amount) {
        positive(amount);
        Account a = get(id);
        long target = Math.addExact(a.balance, amount);
        a.balance = target;
    }

    public synchronized void withdraw(String id, long amount) {
        positive(amount);
        Account a = get(id);
        if (amount > a.balance) throw new IllegalStateException("insufficient funds");
        a.balance -= amount;
    }

    public synchronized void transfer(String fromId, String toId, long amount) {
        positive(amount);
        if (fromId != null && fromId.equals(toId)) {
            throw new IllegalArgumentException("cannot transfer to same account");
        }
        Account from = get(fromId), to = get(toId);
        if (amount > from.balance) throw new IllegalStateException("insufficient funds");
        long received = Math.addExact(to.balance, amount);
        from.balance -= amount;
        to.balance = received;
    }

    private Account get(String id) {
        Account a = accounts.get(id);
        if (a == null) throw new IllegalArgumentException("unknown account ID");
        return a;
    }

    private static void positive(long n) {
        if (n <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}
