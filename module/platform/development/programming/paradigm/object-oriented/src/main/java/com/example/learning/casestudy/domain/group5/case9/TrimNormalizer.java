package com.example.learning.casestudy.domain.group5.case9;

public final class TrimNormalizer extends Normalizer {
    @Override protected String normalize(String raw) {
        return raw.trim();
    }
}
