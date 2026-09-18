package br.com.integrador2.commons;

import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.integrador2.commons.util.Preconditions;
import org.junit.jupiter.api.Test;

class PreconditionsTest {

    @Test
    void checkNotNullThrowsOnNull() {
        assertThrows(IllegalArgumentException.class,
                () -> Preconditions.checkNotNull(null, "value is required"));
    }

    @Test
    void checkArgumentThrowsWhenFalse() {
        assertThrows(IllegalArgumentException.class,
                () -> Preconditions.checkArgument(false, "condition must hold"));
    }
}
