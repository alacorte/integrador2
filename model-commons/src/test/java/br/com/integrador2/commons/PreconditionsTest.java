package br.com.integrador2.commons;

import static org.junit.Assert.assertThrows;

import br.com.integrador2.commons.util.Preconditions;
import org.junit.Test;

public class PreconditionsTest {

    @Test
    public void checkNotNullThrowsOnNull() {
        assertThrows(IllegalArgumentException.class,
                () -> Preconditions.checkNotNull(null, "value is required"));
    }

    @Test
    public void checkArgumentThrowsWhenFalse() {
        assertThrows(IllegalArgumentException.class,
                () -> Preconditions.checkArgument(false, "condition must hold"));
    }
}
