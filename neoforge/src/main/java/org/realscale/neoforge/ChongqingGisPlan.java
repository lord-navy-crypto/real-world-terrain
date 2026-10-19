package org.realscale.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Strict reader for the GIS-to-block exporter native-UI placement plan.
 * No arbitrary Minecraft commands, NBT, registry identifiers, paths or
 * world-save modifications can be embedded in the user-supplied JSON.
 */
public final class ChongqingGisPlan {
    public record Row(int x0, int x1, int y, int z, String kind,
                      int height, String shell) {
        int length() { return x1 - x0 + 1; }
        long writes() {
            return (long) length() *
                    (1 + ("facade".equals(shell) ? height
                         : "roof".equals(shell) ? 1 : 0));
        }
        int nextOffsetAfter(int previous) {
            // Roof-only interior jumps directly from the foundation to
            // roof Y; intermediate air is NEVER filled or scheduled.
            return "roof".equals(shell) && previous == 0 ? height : previous + 1;
        }
    }

    public final List<Row> rows;
    public final String sourceHash;
    public final String planHash;
    public final String attribution;
    public final String demHash;
    public final int radius;
    public final long blockWrites;

    private ChongqingGisPlan(List<Row> rows, String sourceHash,
                             String planHash, String attribution, String demHash,
                             int radius, long blockWrites) {
        this.rows = List.copyOf(rows);
        this.sourceHash = sourceHash;
        this.planHash = planHash;
        this.attribution = attribution;
        this.demHash = demHash;
        this.radius = radius;
        this.blockWrites = blockWrites;
    }

    public static final Path INPUT =
            Path.of("config", "realscale", "chaotianmen-gis-plan.json");
    public static final String FORMAT = "REALSCALE_CHAOTIANMEN_PLACEMENT_V2";
    private static final String BUILT_IN =
            "/assets/realscale/terrain/chaotianmen_authentic_osm_ui_plan_v2.json.gz.b64";
    private static final String BUNDLED_PLAN_SHA256 =
            "04a380b144d3f002c0157d841edefb68d5a5bfacf6ca07149c43e8f03a858980";
    private static final String BUNDLED_VECTOR_SHA256 =
            "9816d76beb4579e8c7289cce52fdd3f216e323c2e2a320562654005717494657";
    private static final long MAX_SOURCE_BYTES = 40L * 1024 * 1024;
    private static final int MAX_ROWS = 250_000;
    // Real 500m OSM snapshot produces 4,033,166 planned writes.
    // Still hard-cap at 5m; all writes must be deliberately paced by the
    // integrated server on >=256-block-per-tick budget gates.
    private static final long MAX_WRITES = 5_000_000L;

    static String installedDemHash() throws IOException {
        Properties props = new Properties();
        try (InputStream in = ChongqingGisPlan.class.getResourceAsStream(
                "/assets/realscale/terrain/chongqing_chaotianmen_dem_v1.properties")) {
            if (in == null) throw new IOException("Missing real Chongqing DEM");
            props.load(in);
        }
        if (!"CQDEM1".equals(props.getProperty("format")))
            throw new IOException("Unsupported Chongqing DEM format");
        String hash = props.getProperty("source_raw_sha256", "");
        if (!hash.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid embedded DEM checksum");
        return hash;
    }

    private static String text(JsonObject obj, String key) {
        return obj.get(key).getAsString();
    }

    private static int intValue(JsonObject obj, String key) {
        return obj.get(key).getAsInt();
    }

    /** Manual override: explicit offline JSON supplied by a user. */
    public static ChongqingGisPlan read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                || Files.size(path) > MAX_SOURCE_BYTES)
            throw new IOException("No local GIS placement plan or file too large");
        return parse(Files.readAllBytes(path));
    }

    /**
     * Default UI workflow: the pinned, genuine ODbL Chaotianmen GIS plan is
     * ALREADY INSIDE THE MOD JAR. Players never run Python or game commands
     * to use it. No network access or world edits during this read.
     */
    public static ChongqingGisPlan readBundled() throws IOException {
        byte[] packed;
        try (InputStream in=ChongqingGisPlan.class.getResourceAsStream(BUILT_IN)) {
            if (in==null) throw new IOException("Authentic bundled GIS plan missing");
            packed=in.readNBytes(MAX_SOURCE_BYTES+1);
        }
        if (packed.length>MAX_SOURCE_BYTES)
            throw new IOException("Oversized bundled GIS archive");
        byte[] zipped;
        try {
            zipped=Base64.getDecoder().decode(
                    new String(packed,StandardCharsets.US_ASCII).strip());
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid built-in GIS Base64",ex);
        }
        byte[] decoded;
        try (GZIPInputStream gzip=new GZIPInputStream(
                new ByteArrayInputStream(zipped))) {
            decoded=gzip.readNBytes(MAX_SOURCE_BYTES+1);
        }
        if (decoded.length>MAX_SOURCE_BYTES)
            throw new IOException("Decompressed GIS plan exceeds safety cap");
        ChongqingGisPlan plan=parse(decoded);
        if (!plan.planHash.equals(BUNDLED_PLAN_SHA256)
                || !plan.sourceHash.equals(BUNDLED_VECTOR_SHA256))
            throw new IOException("Bundled real GIS data fingerprint mismatch");
        return plan;
    }

    private static ChongqingGisPlan parse(byte[] raw) throws IOException {
        if (raw.length>MAX_SOURCE_BYTES)
            throw new IOException("GIS JSON exceeds safety cap");
        JsonObject root;
        try (Reader reader=new java.io.InputStreamReader(
                new ByteArrayInputStream(raw),StandardCharsets.UTF_8)) {
            root=JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Invalid JSON placement plan",ex);
        }

        try {
            if (!FORMAT.equals(text(root, "format")))
                throw new IOException("Incorrect GIS plan format");
            if (Math.abs(root.get("center_lat").getAsDouble() - 29.5667) > 0.0000001
                    || Math.abs(root.get("center_lon").getAsDouble() - 106.5874) > 0.0000001)
                throw new IOException("GIS coordinates do not match original DEM");
            String demHash = text(root, "dem_source_raw_sha256").toLowerCase(Locale.ROOT);
            if (!demHash.equals(installedDemHash()))
                throw new IOException("DEM hash mismatch: plan generated for another terrain");
            String sourceHash = text(root, "vector_source_sha256");
            if (!sourceHash.matches("[0-9a-f]{64}"))
                throw new IOException("Missing source SHA-256");
            String attribution = text(root, "vector_attribution");
            if (attribution.isBlank() || attribution.length() > 2000)
                throw new IOException("Missing/oversize GIS attribution");
            int radius = intValue(root, "radius_m");
            if (radius < 32 || radius > 500)
                throw new IOException("GIS radius must be 32..500m");
            if (!"schematic_hollow_facade_roof_v2".equals(
                    text(root, "massing_policy"))) {
                throw new IOException("Unsupported building shell policy");
            }
            JsonArray incoming = root.getAsJsonArray("rows");
            if (incoming == null || incoming.size() < 1 || incoming.size() > MAX_ROWS)
                throw new IOException("GIS rows count invalid");
            if (intValue(root, "row_count") != incoming.size())
                throw new IOException("Inconsistent exported GIS row count");

            // Validate source Y against the *actual* immutable DEM at every
            // column. Prevent a tampered file from moving an entire city.
            ChongqingDemTile dem = ChongqingDemTile.load();
            List<Row> rows = new ArrayList<>(incoming.size());
            long writes = 0;
            int lastZ = Integer.MIN_VALUE;
            int lastX = Integer.MIN_VALUE;
            for (var element : incoming) {
                JsonObject r = element.getAsJsonObject();
                int x0 = intValue(r, "x0"), x1 = intValue(r, "x1");
                int z = intValue(r, "z"), y = intValue(r, "y");
                int height = intValue(r, "height");
                String kind = text(r, "kind");
                String shell = text(r, "shell");
                if (!switch (kind) {
                    case "building_footprint", "road", "railway", "water_reference" -> true;
                    default -> false;
                }) throw new IOException("Forbidden block kind in GIS plan");
                if (height < 0 || height > 500 ||
                        (height != 0 && !"building_footprint".equals(kind)))
                    throw new IOException("Unsupported GIS building height");
                if (!("none".equals(shell) || "facade".equals(shell)
                        || "roof".equals(shell))) {
                    throw new IOException("Unknown GIS vertical-shell mode");
                }
                if (("none".equals(shell) && height != 0)
                        || (!"none".equals(shell) && height == 0)) {
                    throw new IOException("Building shell/height mismatch");
                }
                if (x0 < -radius || x1 > radius || x1 < x0 ||
                        x1 - x0 + 1 > 2048 || z < -radius || z > radius)
                    throw new IOException("Out-of-bounds GIS placement row");
                if (y < -64 || y + height >= 960)
                    throw new IOException("GIS placement exceeds Chongqing world height");
                if (z < lastZ || (z == lastZ && x0 <= lastX))
                    throw new IOException("Overlapping or out-of-order GIS rows");
                for (int x = x0; x <= x1; x++) {
                    if (!dem.contains(x, z) ||
                            (int) Math.ceil(dem.metresAt(x, z) + 96) != y)
                        throw new IOException("GIS ground Y differs from embedded DEM");
                }
                Row row = new Row(x0,x1,y,z,kind,height,shell);
                writes += row.writes();
                if (writes > MAX_WRITES)
                    throw new IOException("Placement exceeds 5m-block pilot cap");
                rows.add(row);
                lastZ = z; lastX = x1;
            }
            if (root.get("planned_block_writes").getAsLong() != writes)
                throw new IOException("GIS block-count integrity mismatch");
            String planHash;
            try {
                planHash=HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw));
            } catch (NoSuchAlgorithmException ex) {
                throw new IOException("SHA-256 provider missing", ex);
            }
            return new ChongqingGisPlan(rows,sourceHash,planHash,
                    attribution,demHash,radius,writes);
        } catch (IllegalStateException | NullPointerException
                 | NumberFormatException ex) {
            throw new IOException("Malformed GIS placement schema",ex);
        }
    }
}
