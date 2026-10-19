package org.realscale.neoforge;

import java.nio.file.Files;
import java.nio.file.Path;

/** Zero-dependency one-shot GIS world marker smoke/regression test. */
public final class ChongqingPlacementLockSmoke {
    public static void main(String[] args) throws Exception {
        Path disposable = Files.createTempDirectory("realscale-ctm-qa-");
        Path save = disposable.resolve("original-new-world");
        Files.createDirectories(save);
        String plan = "a".repeat(64), osm = "b".repeat(64);
        if (ChongqingPlacementLock.alreadyClaimed(save))
            throw new AssertionError("New world falsely locked");
        ChongqingPlacementLock.claim(save, plan, osm);
        if (!ChongqingPlacementLock.alreadyClaimed(save))
            throw new AssertionError("Source lock did not survive disk write");
        String contents=Files.readString(ChongqingPlacementLock.marker(save));
        if (!contents.contains(plan) || !contents.contains(osm)
                || !contents.contains("restore") && !contents.contains("Restore"))
            throw new AssertionError("World lock provenance incomplete");
        try {
            ChongqingPlacementLock.claim(save,plan,osm);
            throw new AssertionError("Second placement attempt overwrote lock");
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            // Expected: even an identical plan cannot silently replay on restart.
        }
        Path another=disposable.resolve("another-disposable-world");
        Files.createDirectories(another);
        ChongqingPlacementLock.claim(another,plan,osm);
        if (!ChongqingPlacementLock.alreadyClaimed(another))
            throw new AssertionError("Other new world should have distinct marker");
        try {
            ChongqingPlacementLock.claim(disposable.resolve("missing-world"),plan,osm);
            throw new AssertionError("Missing save directory accepted");
        } catch (java.io.IOException expected) {}
        try {
            ChongqingPlacementLock.claim(disposable,"fake-hash",osm);
            throw new AssertionError("Malformed fingerprint accepted");
        } catch (java.io.IOException expected) {}
        System.out.println("CHONGQING ONE-SHOT WORLD LOCK PASS");
    }
}
