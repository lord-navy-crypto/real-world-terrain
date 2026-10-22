package org.realscale.neoforge;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public final class ExactPerlinNoiseMemoSmoke {
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private static double wrap(double x){
        return Math.sin(x*0.00031)+Math.cos(x*0.031);
    }
    public static void main(String[] args)throws InterruptedException{
        ExactPerlinNoiseMemo m=new ExactPerlinNoiseMemo();
        Object a=new Object(),b=new Object();
        double x=12.5,y=-4.0,z=319.25;
        check(!m.get(a,x,y,z,0.0,0.0,false),"cold miss");
        double original=wrap(x);
        m.put(a,x,y,z,0.0,0.0,false,original);
        check(m.get(a,x,y,z,0.0,0.0,false),"identical argument hit");
        check(Double.doubleToRawLongBits(m.value())==
                Double.doubleToRawLongBits(original),"bit-identical stored double");
        check(!m.get(b,x,y,z,0.0,0.0,false),"separate seeded noise owner");
        check(!m.get(a,x,y,z,-0.0,0.0,false),"signed zero yScale must differ");
        check(!m.get(a,x,y,z,0.0,-0.0,false),"signed zero yMax must differ");
        check(!m.get(a,x,y,z,0.0,0.0,true),"useOrigin affects value");
        check(!m.get(a,Double.longBitsToDouble(0x7ff8000000000001L),
                y,z,0,0,false),"NaN bit pattern is part of exact key");
        Random random=new Random(20261022L);
        for(int i=0;i<100000;i++){
            double xx=random.nextInt(4096)-2048,yy=random.nextInt(2096)-1024;
            double zz=random.nextInt(4096)-2048;
            double expected=wrap(xx+yy*0.5+zz*0.25);
            m.put(a,xx,yy,zz,0.1,0.2,false,expected);
            check(m.get(a,xx,yy,zz,0.1,0.2,false),
                    "fresh exact triple must always hit");
            check(Double.doubleToRawLongBits(m.value())==
                    Double.doubleToRawLongBits(expected),"random value parity");
        }
        m.verifiedReplay(original,original);
        boolean threw=false;
        try{m.verifiedReplay(-0.0,+0.0);}catch(AssertionError ex){threw=true;}
        check(threw,"raw-bit replay must reject signed zero mismatch");
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread other=new Thread(()->{
            try{
                ExactPerlinNoiseMemo n=ExactPerlinNoiseMemo.current();
                check(n!=ExactPerlinNoiseMemo.current() ? false : true,
                        "thread-local cache must be stable");
                check(!n.get(a,x,y,z,0,0,false),
                        "another worker must not inherit worldgen values");
            }catch(Throwable t){failure.set(t);}
        });
        other.start();other.join();
        if(failure.get()!=null)throw new AssertionError(failure.get());
        System.out.println("REALSCALE_PERLIN_EXACT_MEMO_SMOKE_PASS samples=100000");
    }
}
