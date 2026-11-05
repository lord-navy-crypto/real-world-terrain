package org.realscale.neoforge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A strict before/after oracle for Phase 4: every compared sample must return
 * the same raw IEEE-754 bits as the frozen pre-optimization Nether algorithm.
 */
public final class GrandNetherCaveNetworkParitySmoke {
    private GrandNetherCaveNetworkParitySmoke() {}

    private static void compare(double x, double y, double z) {
        double oldValue = GrandNetherCaveNetworkReference.strength(x, y, z);
        double newValue = GrandNetherCaveNetwork.strength(x, y, z);
        if (Double.doubleToRawLongBits(oldValue) != Double.doubleToRawLongBits(newValue))
            throw new AssertionError("Nether cave density changed at " + x + "," + y + "," + z
                    + " old=" + oldValue + " new=" + newValue);
    }

    public static void main(String[] args) {
        List<double[]> probes = new ArrayList<>();
        for (int gx = -3; gx <= 3; gx++) {
            for (int gz = -3; gz <= 3; gz++) {
                for (int layer = 0; layer < 2; layer++) {
                    var center = GrandNetherCaveNetwork.hall(gx, gz, layer);
                    for (int dx : new int[]{-55, -35, -11, 0, 11, 35, 55})
                        for (int dz : new int[]{-35, 0, 35})
                            for (int dy : new int[]{-32, -10, 0, 10, 32})
                                probes.add(new double[]{center.x()+dx,center.y()+dy,center.z()+dz});
                    for (int gallery = 0; gallery < 2; gallery++) {
                        var side = GrandNetherCaveNetwork.satellite(gx,gz,layer,gallery);
                        for (int i = 0; i <= 16; i++) {
                            double t = i / 16.0;
                            probes.add(new double[]{center.x()+(side.x()-center.x())*t,
                                    center.y()+(side.y()-center.y())*t,
                                    center.z()+(side.z()-center.z())*t});
                        }
                    }
                    for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        var neighbor = GrandNetherCaveNetwork.hall(gx+d[0],gz+d[1],layer);
                        for (int i = 0; i <= 24; i++) {
                            double t = i / 24.0;
                            double x = center.x()+(neighbor.x()-center.x())*t;
                            double y = center.y()+(neighbor.y()-center.y())*t;
                            double z = center.z()+(neighbor.z()-center.z())*t;
                            probes.add(new double[]{x,y,z});
                            probes.add(new double[]{x+5.125,y-2.375,z-4.5});
                        }
                    }
                }
                var lower = GrandNetherCaveNetwork.hall(gx,gz,0);
                var upper = GrandNetherCaveNetwork.hall(gx,gz,1);
                for (int i = 0; i <= 32; i++) {
                    double t = i/32.0;
                    probes.add(new double[]{lower.x()+(upper.x()-lower.x())*t,
                            lower.y()+(upper.y()-lower.y())*t,
                            lower.z()+(upper.z()-lower.z())*t});
                }
            }
        }
        Random random = new Random(20261015L);
        for (int i = 0; i < 12000; i++) {
            probes.add(new double[]{random.nextInt(4097)-2048+0.25,
                    140+random.nextInt(305)+0.5,
                    random.nextInt(4097)-2048-0.125});
        }

        for (double[] xyz : probes) compare(xyz[0],xyz[1],xyz[2]);
        // Reversed and shuffled access detects stale 256m-cell cache keys.
        Collections.shuffle(probes,new Random(18978L));
        for (int i = probes.size()-1; i >= 0; i--) {
            double[] xyz=probes.get(i);
            compare(xyz[0],xyz[1],xyz[2]);
        }

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread[] workers=new Thread[4];
        for (int n=0;n<workers.length;n++) {
            final int worker=n;
            workers[n]=new Thread(() -> {
                try {
                    for (int i=worker;i<probes.size();i+=4) {
                        double[] xyz=probes.get(i);
                        compare(xyz[0],xyz[1],xyz[2]);
                    }
                } catch(Throwable t) {
                    error.compareAndSet(null,t);
                }
            },"nether-bit-parity-"+n);
            workers[n].start();
        }
        for (Thread thread : workers) {
            try {thread.join();}
            catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Nether parity test interrupted",e);
            }
        }
        if(error.get()!=null) throw new AssertionError("Concurrent Nether density changed",error.get());
        System.out.println("TVA_NETHER_BIT_EXACT_PASS: " + probes.size()
                + " coordinates; baseline/current forward, shuffled, and parallel comparisons");
    }
}
