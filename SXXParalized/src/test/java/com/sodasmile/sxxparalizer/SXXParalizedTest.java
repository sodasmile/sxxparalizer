package com.sodasmile.sxxparalizer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * TODO anderssm: Dokumentèr.
 */
public class SXXParalizedTest {

    @Test
    public void testCmdForThisHost() {
        assertFalse(SXXParalized.commandForCurrentHost("host1", "sleep 3"));
        assertFalse(SXXParalized.commandForCurrentHost("host1", "% host1 sleep 3"));
        assertFalse(SXXParalized.commandForCurrentHost("host1", "% host1: sleep 3"));
        assertFalse(SXXParalized.commandForCurrentHost("host1", "%host1 : sleep 3"));
        assertFalse(SXXParalized.commandForCurrentHost("host1", "%host2: sleep 3"));
        assertTrue(SXXParalized.commandForCurrentHost("host1", "%host1: sleep 3"));
    }

    @Test
    public void testHostSpecificCommand() {
        assertFalse(SXXParalized.isHostSpecificCommand("sleep 3"));
        assertFalse(SXXParalized.isHostSpecificCommand("sleep 3%"));
        assertTrue(SXXParalized.isHostSpecificCommand("%host1: sleep 3"));
    }

    @Test
    public void testCleanupCommand() {
        assertEquals("sleep 3", SXXParalized.cleanupCommand("%host1: sleep 3"));
        assertEquals("sleep 3", SXXParalized.cleanupCommand("sleep 3"));
        assertEquals("wget http://osloisdev5.statnett.no/nexus/content/repositories/statnett-snapshots/no/statnett/larm/larm-ear/1.0-SNAPSHOT/larm-ear-1.0-SNAPSHOT-`hostname -s`.ear",
                SXXParalized.cleanupCommand("wget http://osloisdev5.statnett.no/nexus/content/repositories/statnett-snapshots/no/statnett/larm/larm-ear/1.0-SNAPSHOT/larm-ear-1.0-SNAPSHOT-`hostname -s`.ear"));
    }
}
