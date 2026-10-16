"""Shared exact reproduction of SkyIslandLayout's deterministic test centers.
Keep in sync with the Java layout; tests check the correspondence.
"""
MASK=(1<<64)-1
GRID=320
SATELLITES=((0,0),(80,0),(-80,0),(0,80),(0,-80))

def mix(value):
    value &= MASK
    value ^= value >> 30
    value = (value*0xbf58476d1ce4e5b9)&MASK
    value ^= value >> 27
    value = (value*0x94d049bb133111eb)&MASK
    return (value ^ (value>>31))&MASK

def site_at(x,z):
    gx=(x+GRID//2)//GRID
    gz=(z+GRID//2)//GRID
    h=mix((gx*0x9e3779b97f4a7c15) ^ (gz*0xc2b2ae3d27d4eb4f)
          ^ 0x3bc7f54a83e1d209)
    return (gx*GRID-48+(h%96),gz*GRID-48+((h>>15)%96),
            183+((h>>30)%21),10+((h>>47)%5))

def archipelago_at_origin():
    x,z,top,radius=site_at(0,0)
    result=[(x,z,top)]
    for slot in range(1,len(SATELLITES)):
        h=mix(((x & MASK)<<32) ^ (z&0xffffffff)
              ^ ((0x9e3779b97f4a7c15*slot)&MASK))
        dx,dz=SATELLITES[slot]
        result.append((x+dx,z+dz,top+(h%7)-3))
    return tuple(result)

if __name__=="__main__":
    print(archipelago_at_origin())
