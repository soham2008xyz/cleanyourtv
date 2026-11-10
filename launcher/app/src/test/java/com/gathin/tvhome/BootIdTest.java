package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BootIdTest {
    @Test
    public void kernelIdIsStableAndNonNegative() {
        int a = BootId.fromKernelId("3b1f8c2e-7a4d-4e6b-9c05-2d8e1f6a7b90\n");
        assertTrue(a >= 0);
        assertEquals(a, BootId.fromKernelId("3b1f8c2e-7a4d-4e6b-9c05-2d8e1f6a7b90"));
    }

    @Test
    public void differentBootsGiveDifferentIds() {
        assertNotEquals(BootId.fromKernelId("3b1f8c2e-7a4d-4e6b-9c05-2d8e1f6a7b90"),
                BootId.fromKernelId("6a1c0e55-2b7d-4f0e-9d63-1c2a7f8e9b01"));
    }

    @Test
    public void nothingUsableIsMinusOne() {
        assertEquals(-1, BootId.fromKernelId(null));
        assertEquals(-1, BootId.fromKernelId("  "));
    }
}
