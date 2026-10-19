package org.realscale.neoforge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * A fail-closed, one-shot world marker for the experimental GIS overlay.
 * Never silently restart a partially completed 4m-write construction.
 *
 * The marker is written BEFORE any block changes and is not automatically
 * erased on pause, quit, reload or completion. A new disposable save from
 * an earlier backup is needed for a different placement attempt.
 *
 * No command/script execution, no symlink following, no arbitrary user path.
 */
public final class ChongqingPlacementLock {
    public static final String NAME = "realscale-chaotianmen-gis-lock.json";

    private ChongqingPlacementLock() {}

    public static Path marker(Path worldRoot) {
        return worldRoot.resolve(NAME);
    }

    public static boolean alreadyClaimed(Path worldRoot) {
        return Files.exists(marker(worldRoot), LinkOption.NOFOLLOW_LINKS);
    }

    public static void claim(Path worldRoot, String planHash,
                             String sourceHash) throws IOException {
        if (worldRoot == null || !Files.isDirectory(worldRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(worldRoot)) {
            throw new IOException("Refusing GIS write outside an existing real save directory");
        }
        if (planHash == null || !planHash.matches("[0-9a-f]{64}")
                || sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid plan/source fingerprint");
        }
        // CREATE_NEW is atomic: no TOCTOU check-and-overwrite on restart.
        String contents = "{\n"
                + "  \"format\": \"CHONGQING_GIS_ONE_SHOT_LOCK_V1\",\n"
                + "  \"plan_sha256\": \"" + planHash + "\",\n"
                + "  \"osm_sha256\": \"" + sourceHash + "\",\n"
                + "  \"created_utc\": \"" + Instant.now() + "\",\n"
                + "  \"message\": \"Do not delete to replay. Restore a previously backed-up disposable world for a new run. This lock never indicates that placement finished.\"\n"
                + "}\n";
        Files.writeString(marker(worldRoot), contents, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
