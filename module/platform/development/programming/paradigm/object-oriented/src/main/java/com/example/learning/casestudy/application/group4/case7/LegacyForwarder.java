package com.example.learning.casestudy.application.group4.case7;

import com.example.learning.casestudy.port.group4.case7.FormatPort;

public final class LegacyForwarder {
    private final FormatPort port;
    public LegacyForwarder(FormatPort port) { this.port=port; }
    public String execute(String text) { return port.format(text); }
}
