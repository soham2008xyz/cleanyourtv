package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * now_playing: the media session another app is playing, for the top bar's Now Playing pill. Only listens between
 * start() and stop(), which Home calls on resume and pause, so nothing is read while Home is in the background.
 * Without notification access for NowPlayingListener the sessions can't be read, and the pill stays hidden.
 */
final class NowPlaying {
    interface Listener {
        /** The session playing now, or null when nothing plays (or access is not granted). */
        void onNowPlaying(MediaController playing);
    }

    private final MediaSessionManager sessions;
    private final ComponentName access;
    private final String ownPackage;
    private final Handler handler;
    private final Listener listener;
    private final List<MediaController> watched = new ArrayList<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::watch;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            update(false);
        }

        @Override public void onMetadataChanged(MediaMetadata metadata) {
            update(true);
        }

        @Override public void onSessionDestroyed() {
            update(false);
        }
    };
    private boolean started;
    /** The session last handed to the listener (null: nothing), so a state change that keeps it is not sent again. */
    private MediaSession.Token shown;

    NowPlaying(Context context, Handler handler, Listener listener) {
        sessions = context.getSystemService(MediaSessionManager.class);
        access = new ComponentName(context, NowPlayingListener.class);
        ownPackage = context.getPackageName();
        this.handler = handler;
        this.listener = listener;
    }

    void start() {
        if (started || sessions == null) return;
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, access, handler);
            started = true;
            shown = null;
            watch(sessions.getActiveSessions(access));
        } catch (SecurityException e) {
            // No notification access: cmd notification allow_listener (config.xml now_playing).
            listener.onNowPlaying(null);
        }
    }

    void stop() {
        if (!started) return;
        started = false;
        sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        unwatch();
    }

    private void watch(List<MediaController> controllers) {
        unwatch();
        if (controllers != null) {
            for (MediaController c : controllers) {
                if (ownPackage.equals(c.getPackageName())) continue;
                c.registerCallback(callback, handler);
                watched.add(c);
            }
        }
        update(false);
    }

    private void unwatch() {
        for (MediaController c : watched) c.unregisterCallback(callback);
        watched.clear();
    }

    /** Tells the listener when the session to show changes, or its song does (metadataChanged). */
    private void update(boolean metadataChanged) {
        if (!started) return;
        int[] states = new int[watched.size()];
        for (int i = 0; i < states.length; i++) {
            PlaybackState state = watched.get(i).getPlaybackState();
            states[i] = state == null ? PlaybackState.STATE_NONE : state.getState();
        }
        int i = pick(states);
        MediaController playing = i < 0 ? null : watched.get(i);
        MediaSession.Token token = playing == null ? null : playing.getSessionToken();
        if (!metadataChanged && Objects.equals(token, shown)) return;
        shown = token;
        listener.onNowPlaying(playing);
    }

    /**
     * The session to show, by index in the system's order (most recently active first): the first one playing,
     * else the first one buffering, else -1. A paused session is left out, or a song paused hours ago would stay
     * on Home.
     */
    static int pick(int[] states) {
        int buffering = -1;
        for (int i = 0; i < states.length; i++) {
            if (states[i] == PlaybackState.STATE_PLAYING) return i;
            if (buffering < 0 && states[i] == PlaybackState.STATE_BUFFERING) buffering = i;
        }
        return buffering;
    }

    /** "Title · Artist", or whichever of the two there is; null with neither (the pill shows the app's name). */
    static String line(CharSequence title, CharSequence artist) {
        String t = title == null ? "" : title.toString().trim();
        String a = artist == null ? "" : artist.toString().trim();
        // Plain String checks, not TextUtils, so the JVM tests can call this.
        if (t.isEmpty()) return a.isEmpty() ? null : a;
        return a.isEmpty() || a.equals(t) ? t : t + " · " + a;
    }
}
