#!/usr/bin/env python3
"""Exact, independent Python 16x16 monotone DEM sampling reference.

Port of RealDemSeamlessMath.measuredHeight in the Minecraft NeoForge mod.
This tests actual Anvil terrain against what the production algorithm intends,
not the different legacy bilinear sampler.
"""
import gzip
import math
import struct
from functools import lru_cache
from pathlib import Path

class MonotoneDem:
    def __init__(self,source):
        raw=gzip.decompress(Path(source).read_bytes())
        if raw[:4]!=b"RGT1":raise ValueError("Not a verified RGT1 DEM")
        width,height,x0,z0,sea=struct.unpack_from(">iiiii",raw,4)
        cell,offset=struct.unpack_from(">dd",raw,24)
        if (width,height,cell,sea)!=(16,16,32.0,63):
            raise ValueError("Unknown source DEM grid or datum")
        self.x0=x0;self.z0=z0;self.maxx=x0+480;self.maxz=z0+480
        self.grid=[
            [sea+offset+v for v in struct.unpack_from(">16f",raw,40+z*16*4)]
            for z in range(16)
        ]
        if not all(math.isfinite(v) for row in self.grid for v in row):
            raise ValueError("Nonfinite source elevations")

    @staticmethod
    def clip(v,lo,hi):
        return max(lo,min(hi,v))

    @staticmethod
    def monotone_slope(left,right):
        if left*right<=0.0:return 0.0
        return 2.0*left*right/(left+right)

    @classmethod
    def segment(cls,prev,a,b,nxt,t):
        d=b-a
        if d==0.0:return a
        m0=cls.monotone_slope(a-prev,d)
        m1=cls.monotone_slope(d,nxt-b)
        t2=t*t;t3=t2*t
        result=(2*t3-3*t2+1)*a+(t3-2*t2+t)*m0+(-2*t3+3*t2)*b+(t3-t2)*m1
        return cls.clip(result,min(a,b),max(a,b))

    def corner(self,row,col):
        return self.grid[int(self.clip(row,0,15))][int(self.clip(col,0,15))]

    def sample_row(self,row,ix,t):
        a=self.corner(row,ix);b=self.corner(row,ix+1)
        prev=self.corner(row,ix-1) if ix>0 else 2*a-b
        nxt=self.corner(row,ix+2) if ix<14 else 2*b-a
        return self.segment(prev,a,b,nxt,t)

    @lru_cache(maxsize=25000)
    def measured_height(self,x,z):
        x=self.clip(x,self.x0,self.maxx);z=self.clip(z,self.z0,self.maxz)
        gx=(x-self.x0)/32.;gz=(z-self.z0)/32.
        ix=min(14,math.floor(gx));iz=min(14,math.floor(gz))
        a=gx-ix;b=gz-iz
        r0=self.sample_row(iz-1,ix,a)
        r1=self.sample_row(iz,ix,a)
        r2=self.sample_row(iz+1,ix,a)
        r3=self.sample_row(iz+2,ix,a)
        lower=r0 if iz>0 else 2*r1-r2
        upper=r3 if iz<14 else 2*r2-r1
        return self.segment(lower,r1,r2,upper,b)

    @staticmethod
    def taper(distance):
        length=min(abs(distance),128.0)
        softened=length-length*length/256.0
        return math.copysign(softened,distance)

    @lru_cache(maxsize=25000)
    def extended_height(self,x,z):
        cx=self.clip(x,self.x0,self.maxx)
        cz=self.clip(z,self.z0,self.maxz)
        base=self.measured_height(cx,cz)
        dx=x-cx;dz=z-cz
        if dx:
            direction=32 if dx<0 else -32
            interior=self.measured_height(cx+direction,cz)
            base+=self.clip((interior-base)/direction,-1.25,1.25)*self.taper(dx)
        if dz:
            direction=32 if dz<0 else -32
            interior=self.measured_height(cx,cz+direction)
            base+=self.clip((interior-base)/direction,-1.25,1.25)*self.taper(dz)
        return self.clip(base,-64,2031)

    def weight(self,x,z,fade=2048):
        dx=x-self.clip(x,self.x0,self.maxx)
        dz=z-self.clip(z,self.z0,self.maxz)
        t=self.clip(math.hypot(dx,dz)/fade,0,1)
        return 1-t*t*(3-2*t)
