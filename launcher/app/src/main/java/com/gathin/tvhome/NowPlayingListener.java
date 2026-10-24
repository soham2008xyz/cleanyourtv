package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * now_playing: Android hands other apps' media sessions (MediaSessionManager.getActiveSessions) only to a
 * notification listener the owner has granted. This one reads no notifications; it exists for that check. Disabled
 * in the manifest and switched on by MainActivity only while now_playing is on.
 */
public class NowPlayingListener extends NotificationListenerService {
}
