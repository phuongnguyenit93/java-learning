package com.example.learning.secret

interface SecretProvider {

    String findSecret(
            String name
    )
}