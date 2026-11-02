package com.example.learning.casestudy.port.group6.case2;

import com.example.learning.casestudy.domain.group6.case2.Transaction;
import com.example.learning.casestudy.dto.group6.case2.TxView;

public interface TxPolicy { TxView apply(Transaction transaction); }
