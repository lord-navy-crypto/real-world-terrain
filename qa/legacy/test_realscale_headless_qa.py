import gzip, json, struct, tempfile, unittest, zlib
from pathlib import Path
import realscale_headless_qa as q


def ns(s):
    b=s.encode();return struct.pack('>H',len(b))+b

def named(t,n,v):return bytes([t])+ns(n)+v

def comp(d):return b''.join(d)+b'\0'

def root(tags):return b'\x0a\x00\x00'+comp(tags)

def compound_tag(n,tags):return named(10,n,comp(tags))

def int_tag(n,v):return named(3,n,struct.pack('>i',v))

def long_arr_tag(n,values):return named(12,n,struct.pack('>i',len(values))+b''.join(struct.pack('>q',v if v < (1<<63) else v-(1<<64)) for v in values))

def byte_tag(n,v):return named(1,n,struct.pack('>b',v))

def str_tag(n,s):return named(8,n,ns(s))

def list_of_compounds(n,items):return named(9,n,b'\x0a'+struct.pack('>i',len(items))+b''.join(comp(x) for x in items))

def longpack(size,bits,val):
    ep=64//bits
    x=[0]*((size+ep-1)//ep)
    for i in range(size):x[i//ep]|=(val&((1<<bits)-1))<<((i%ep)*bits)
    return x

class QAParserTests(unittest.TestCase):
    def test_01_nbt_parser_and_chunk_height(self):
        heights=longpack(256,12,128) # world_surface first available = y=-64 +128 =64, top 63
        raw=root([compound_tag('Heightmaps',[long_arr_tag('WORLD_SURFACE',heights)]),
                  named(9,'sections',b'\x0a'+struct.pack('>i',1)+comp([byte_tag('Y',3),
                    compound_tag('block_states',[list_of_compounds('palette',[[str_tag('Name','minecraft:water')]])])]))])
        o=q.read_nbt(raw)
        self.assertEqual(q.surface_height(o,3,5),63)
        self.assertEqual(q.get_block(o,3,63,5),'minecraft:water')
    def test_02_palette_unpack(self):
        a=longpack(4096,4,7)
        for k in (0,5,15,16,255,256,4095):self.assertEqual(q.unpack_palette_index(a,k,4),7)
    def test_03_region_decode(self):
        raw=root([int_tag('xPos',0),int_tag('zPos',0)])
        dat=zlib.compress(raw)
        chunk=struct.pack('>I',len(dat)+1)+b'\x02'+dat
        header=bytearray(8192)
        header[:4]=(2).to_bytes(3,'big')+b'\x01'
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/'r.0.0.mca'
            p.write_bytes(bytes(header)+chunk+b'\0'*(4096-len(chunk)))
            data=list(q.iter_region_chunks(p))
            self.assertEqual(len(data),1)
            self.assertEqual(data[0]['xPos'],0)
    def test_04_pack_validation(self):
        p=Path(__file__).parent/'RealScaleTerrain_DataPack_1.21.1_v0.4_AUDIT.zip'
        self.assertEqual(q.check_pack(p),(-64,2096))
    def test_05_server_properties(self):
        cfg=q.make_properties(1234)
        self.assertIn('level-type=realscale:ultimate',cfg)
        self.assertIn('level-seed=1234',cfg)
        self.assertIn('initial-enabled-packs=vanilla,file/',cfg)

if __name__=='__main__':unittest.main(verbosity=2)