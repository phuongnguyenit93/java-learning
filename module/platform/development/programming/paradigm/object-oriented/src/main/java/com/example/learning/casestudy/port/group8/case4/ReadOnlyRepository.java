package com.example.learning.casestudy.port.group8.case4;

import com.example.learning.casestudy.model.group8.case4.Item;
import java.util.Optional;

public interface ReadOnlyRepository { public Optional<Item> find(String id); }
