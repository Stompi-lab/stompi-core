import importlib.util
from collections import deque
from http.server import ThreadingHTTPServer
import urllib.request
import json
import socket
import struct
import threading

spec = importlib.util.spec_from_file_location('bridge', 'stompi-stratum.py')
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)

class FakeRPC:
    def __init__(self): self.blocks=[]
    def call(self,method,params=None):
        if method=='getblocktemplate': return {'height':1,'version':0x20000000,'previousblockhash':'00'*32,'coinbasevalue':50*100000000,'bits':'207fffff','curtime':1790244001,'mintime':1790244001,'transactions':[],'default_witness_commitment':'6a24aa21a9ed'+'00'*32}
        if method=='getbestblockhash': return '00'*32
        if method=='validateaddress': return {'isvalid':True,'scriptPubKey':'0014'+'11'*20}
        if method=='submitblock': self.blocks.append(bytes.fromhex(params[0]));return None
        raise AssertionError(method)

b.DIFF1=b.target('207fffff')  # isolated low-difficulty protocol simulation
fake=FakeRPC()
server=b.Pool(('127.0.0.1',0),b.Miner)
server.slots=threading.BoundedSemaphore(32)
server.rpc=fake;server.share_diff=1
server.tip=None
server.metrics_lock=threading.Lock();server.miners={};server.blocks=deque(maxlen=100)
server.height=None;server.started_at=0
threading.Thread(target=server.serve_forever,daemon=True).start()
sock=socket.create_connection(server.server_address)
f=sock.makefile('rwb',buffering=0)
def send(i,method,params):
    f.write(json.dumps({'id':i,'method':method,'params':params}).encode()+b'\n')
    return json.loads(f.readline())
assert send(1,'mining.configure',[['version-rolling'],{'version-rolling.mask':'ffffffff'}])['result']['version-rolling.mask']=='1fffe000'
ex1=send(2,'mining.subscribe',['bitaxe'])['result'][1]
assert send(3,'mining.authorize',['StompiLegacyAddress.bitaxe','x'])['result'] is True
assert json.loads(f.readline())['method']=='mining.set_difficulty'
job=json.loads(f.readline())['params']
jid,prev,cb1,cb2,branches,version,bits,ntime,_=job
assert branches==[]
extra2='00000000';coinbase=bytes.fromhex(cb1+ex1+extra2+cb2)
root=b.h(coinbase)
for nonce in range(100):
    header=struct.pack('<I',0x20002000)+b.swap4(bytes.fromhex(prev))+root+struct.pack('<III',int(ntime,16),int(bits,16),nonce)
    if int.from_bytes(b.h(header),'little')<=b.target(bits):break
else:raise AssertionError('nonce not found')
assert send(4,'mining.submit',['StompiLegacyAddress.bitaxe',jid,extra2,ntime,f'{nonce:08x}','80000000'])['error'][1]=='version bits outside mask'
assert send(5,'mining.submit',['StompiLegacyAddress.bitaxe',jid,extra2,ntime,f'{nonce:08x}','00002000'])['result'] is True
assert fake.blocks[0]==header+b'\x01'+b.serialize_coinbase(coinbase,True)
assert b'\x6a\x24\xaa\x21\xa9\xed' in fake.blocks[0]
assert server.snapshot()['blocks'][0]['height']==1
print('Stratum bridge submitted a serialized block to Core RPC')
sock.close();server.shutdown();server.server_close()

web=ThreadingHTTPServer(('127.0.0.1',0),b.Dashboard)
web.pool=server
threading.Thread(target=web.serve_forever,daemon=True).start()
url=f'http://127.0.0.1:{web.server_port}'
assert json.load(urllib.request.urlopen(url+'/api/status'))['name']=='Stompi Solo Pool'
assert b'STOMPI' in urllib.request.urlopen(url+'/').read()
web.shutdown();web.server_close()

# The explicit unlimited setting must accept connections without a semaphore.
unlimited=b.Pool(('127.0.0.1',0),b.Miner)
unlimited.slots=None
unlimited.rpc=fake;unlimited.share_diff=1;unlimited.tip=None
unlimited.metrics_lock=threading.Lock();unlimited.miners={};unlimited.blocks=deque(maxlen=100)
unlimited.height=None;unlimited.started_at=0
threading.Thread(target=unlimited.serve_forever,daemon=True).start()
peers=[socket.create_connection(unlimited.server_address) for _ in range(36)]
assert len(peers)==36
for peer in peers: peer.close()
unlimited.shutdown();unlimited.server_close()
