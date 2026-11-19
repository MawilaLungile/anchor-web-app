package com.anchor.repo;

/**
 * Marker for the repository package. Each repository below scopes every query by user id,
 * so one account can never read another account's data.
 */
public final class Repositories {

    private Repositories() {
    }
}
