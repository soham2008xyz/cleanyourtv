package com.gathin.tvhome;

import android.content.Context;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * A number that is the same for the whole of one boot and changes at the next, for the "once per boot" checks
 * (HomeKeyService's boot probe, open_on_start, boot_to_live_tv). Settings.Global.BOOT_COUNT is that number from
 * Android 7; before it (Fire OS 5, Android 5.1) the setting is missing and reads -1, which turned those checks off.
 * There the kernel's per-boot UUID stands in: /proc/sys/kernel/random/boot_id is new at every boot and readable by
 * apps. -1 when neither is available, which keeps the checks off as before.
 */
final class BootId {
    private static final String BOOT_ID = "/proc/sys/kernel/random/boot_id";

    private BootId() {
    }

    static int current(Context context) {
        int count = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        if (count >= 0) return count;
        try (BufferedReader in = new BufferedReader(new FileReader(BOOT_ID))) {
            return fromKernelId(in.readLine());
        } catch (Exception e) {
            return -1;
        }
    }

    /** The kernel's boot UUID as a non-negative int; -1 for nothing usable. */
    static int fromKernelId(String uuid) {
        if (uuid == null) return -1;
        String id = uuid.trim();
        if (id.isEmpty()) return -1;
        return id.hashCode() & 0x7fffffff;
    }
}
