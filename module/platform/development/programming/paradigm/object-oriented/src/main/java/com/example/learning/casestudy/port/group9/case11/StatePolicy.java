package com.example.learning.casestudy.port.group9.case11;

import com.example.learning.casestudy.domain.group9.case11.Phase;

public interface StatePolicy { Phase transition(Phase next); }
