package com.gathin.tvhome;

import android.media.session.PlaybackState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NowPlayingTest {
    @Test
    public void picksTheFirstPlayingSession() {
        assertEquals(1, NowPlaying.pick(new int[] {PlaybackState.STATE_PAUSED, PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_PLAYING}));
    }

    @Test
    public void playingBeatsAnEarlierBufferingSession() {
        assertEquals(1, NowPlaying.pick(new int[] {PlaybackState.STATE_BUFFERING, PlaybackState.STATE_PLAYING}));
        assertEquals(0, NowPlaying.pick(new int[] {PlaybackState.STATE_BUFFERING, PlaybackState.STATE_STOPPED}));
    }

    @Test
    public void pausedOrStoppedSessionsShowNothing() {
        assertEquals(-1, NowPlaying.pick(new int[] {PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED,
                PlaybackState.STATE_NONE}));
        assertEquals(-1, NowPlaying.pick(new int[0]));
    }

    @Test
    public void lineJoinsTitleAndArtist() {
        assertEquals("Blue · Joni Mitchell", NowPlaying.line(" Blue ", "Joni Mitchell"));
        assertEquals("Blue", NowPlaying.line("Blue", ""));
        assertEquals("Blue", NowPlaying.line("Blue", "Blue"));
        assertEquals("Joni Mitchell", NowPlaying.line(null, "Joni Mitchell"));
        assertNull(NowPlaying.line(" ", null));
    }

    @Test
    public void onlyBelowDateMovesTheLine() {
        assertTrue(Config.nowPlayingBelowDate("below_date"));
        assertTrue(Config.nowPlayingBelowDate(" Below_Date "));
        assertFalse(Config.nowPlayingBelowDate("top_bar"));
        assertFalse(Config.nowPlayingBelowDate("under"));
        assertFalse(Config.nowPlayingBelowDate(null));
    }
}
