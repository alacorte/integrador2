package br.com.integrador2.commons.util;

/**
 * Utilitario simples de validacao, compartilhado por backend e frontend.
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static void checkNotNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void checkArgument(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
