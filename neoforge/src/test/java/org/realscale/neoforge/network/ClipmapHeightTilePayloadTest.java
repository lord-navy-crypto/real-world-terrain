package org.realscale.neoforge.network;

import org.realscale.neoforge.client.clipmap.ClipmapBiomeMaterial;

import org.junit.jupiter.api.Test;
import java.nio.ShortBuffer;
import static org.junit.jupiter.api.Assertions.*;

final class ClipmapHeightTilePayloadTest {
    private static ClipmapHeightTilePayload payload(short[] samples) {
        return new ClipmapHeightTilePayload(
                "minecraft:overworld", 1L, 10, 16, 0, 8,
                -64, -64, -64, 2032, samples);
    }

    @Test
    void constructorAndAccessorKeepThePayloadArrayDefensive() {
        short[] source = new short[17 * 17];
        source[0] = 1234;
        ClipmapHeightTilePayload payload = payload(source);
        source[0] = 7;
        assertEquals(1L, payload.requestId());
        assertEquals(1234, payload.heights()[0]);

        short[] copy = payload.heights();
        copy[0] = 9;
        assertEquals(1234, payload.heights()[0]);
    }

    @Test
    void copiesSamplesDirectlyIntoCallerOwnedUploadBuffer() {
        short[] source = new short[17 * 17];
        for (int i = 0; i < source.length; i++) source[i] = (short) (i * 13);
        ClipmapHeightTilePayload payload = payload(source);
        ShortBuffer destination = ShortBuffer.allocate(source.length);
        payload.copyHeightsTo(destination);
        destination.flip();
        for (short expected : source) assertEquals(expected, destination.get());
    }

    @Test
    void copiesOnlyTheRequestedRowMajorRegion() {
        short[] source = new short[17 * 17];
        for (int i = 0; i < source.length; i++) source[i] = (short) i;
        ClipmapHeightTilePayload payload = payload(source);
        ShortBuffer destination = ShortBuffer.allocate(5 * 4);
        payload.copyHeightsRegionTo(3, 6, 5, 4, destination);
        destination.flip();
        for (int z = 6; z < 10; z++) {
            for (int x = 3; x < 8; x++) {
                assertEquals(source[z * 17 + x], destination.get());
            }
        }
    }

    @Test
    void rejectsOutOfBoundsRegionsAndInsufficientRegionBuffers() {
        ClipmapHeightTilePayload payload = payload(new short[17 * 17]);
        assertThrows(IllegalArgumentException.class,
                () -> payload.copyHeightsRegionTo(-1, 0, 1, 1,
                        ShortBuffer.allocate(1)));
        assertThrows(IllegalArgumentException.class,
                () -> payload.copyHeightsRegionTo(16, 0, 2, 1,
                        ShortBuffer.allocate(2)));
        assertThrows(IllegalArgumentException.class,
                () -> payload.copyHeightsRegionTo(0, 0, 2, 2,
                        ShortBuffer.allocate(3)));
        assertThrows(IllegalArgumentException.class,
                () -> payload.copyHeightsRegionTo(0, 0, 1, 1, null));
    }

    @Test
    void biomeMaterialsAreDefensiveAndIndexable(){
        short[] h=new short[17*17];byte[] m=new byte[17*17];m[4]=ClipmapBiomeMaterial.FOREST;
        ClipmapHeightTilePayload p=new ClipmapHeightTilePayload("minecraft:overworld",1L,10,16,0,8,-64,-64,-64,2032,h,m);
        m[4]=ClipmapBiomeMaterial.DESERT;assertEquals(ClipmapBiomeMaterial.FOREST,p.biomeMaterialAt(4));
        byte[] copy=p.biomeMaterials();copy[4]=ClipmapBiomeMaterial.SNOW;
        assertEquals(ClipmapBiomeMaterial.FOREST,p.biomeMaterialAt(4));
        assertThrows(IndexOutOfBoundsException.class,()->p.biomeMaterialAt(-1));
    }
    @Test
    void rejectsBiomeMaterialArrayWithWrongLength(){
        assertThrows(IllegalArgumentException.class,()->new ClipmapHeightTilePayload(
            "minecraft:overworld",1L,10,16,0,8,-64,-64,-64,2032,new short[17*17],new byte[3]));
    }

    @Test
    void rejectsNonPositiveRequestGeneration() {
        assertThrows(IllegalArgumentException.class, () ->
                new ClipmapHeightTilePayload("minecraft:overworld", 0L,
                        10, 16, 0, 8, -64, -64, -64, 2032,
                        new short[17 * 17]));
    }

    @Test
    void rejectsInsufficientDestinationCapacity() {
        ClipmapHeightTilePayload payload = payload(new short[17 * 17]);
        assertThrows(IllegalArgumentException.class,
                () -> payload.copyHeightsTo(ShortBuffer.allocate(10)));
        assertThrows(IllegalArgumentException.class,
                () -> payload.copyHeightsTo(null));
    }
}
