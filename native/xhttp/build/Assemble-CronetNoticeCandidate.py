"""Assemble conservative full-source Cronet notice candidate with measured input coverage."""
import argparse
import base64
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import tarfile

parser=argparse.ArgumentParser()
for name in ('workspace','notices-root','source-archive','source-mapping','generated','ndk','clang','output'):
    parser.add_argument('--'+name,required=True,type=Path)
parser.add_argument('--additional-toolchain-headers',type=Path)
a=parser.parse_args();root=a.workspace.resolve();out=a.output.resolve()
assert root in out.parents and not out.exists()
inv=json.loads((a.notices_root/'inventory.json').read_text())
mapping=json.loads(gzip.decompress(a.source_mapping.read_bytes()))
generated=json.loads(gzip.decompress(a.generated.read_bytes()))
assert inv['source_commit']==mapping['sourceCommit']==generated['sourceCommit']=='72a06c9fca0e2d228588c7f3074bf7efff3ff686'
assert mapping['compilerRun']==generated['compilerRun'] and not generated['unresolvedDeclaredInputs']
assert mapping['sourceArchiveSha256']==inv['source_archive_sha256']=='396d4ad6f5bf8ff812f915f0e21281748d7009773fa1bf182cd765f130fd9b19'
assert generated['sourceMappingSha256']==hashlib.sha256(a.source_mapping.read_bytes()).hexdigest()
with a.source_archive.open('rb') as f:assert hashlib.file_digest(f,'sha256').hexdigest()==inv['source_archive_sha256']
docs={}
def add(key,data,origin):
    assert key not in docs and len(data)<=4*1024*1024
    data.decode('utf-8')
    docs[key]=dict(data=data,origin=origin,sha256=hashlib.sha256(data).hexdigest())
for row in inv['source_notices']:
    data=(a.notices_root/'source-documents'/row['path']).read_bytes()
    assert hashlib.sha256(data).hexdigest()==row['sha256']
    add('source:'+row['path'],data,row['path'])
for row in inv['supplemental_notices']:
    data=(a.notices_root/'supplemental-notices'/row['component_id']/row['license_file']).read_bytes()
    assert hashlib.sha256(data).hexdigest()==row['license_sha256']
    add('supplement:'+row['component_id'],data,row['source_url'])
external={}
for label,path in (('ndk',a.ndk),('clang',a.clang)):
    bundle=json.loads(gzip.decompress(path.read_bytes()));keys=[]
    for row in bundle['notices']:
        name=row.get('name',row.get('path'))
        if name=='source.properties':continue
        data=base64.b64decode(row['originalBytesBase64'],validate=True)
        assert len(data)==row['bytes'] and hashlib.sha256(data).hexdigest()==row['sha256']
        key='external:'+label+'/'+name;add(key,data,name);keys.append(key)
    for row in bundle['files']:
        assert row['matches']
        external[row['sourceRelativePath']]=(row['observedCompilerSha256'],keys)
if a.additional_toolchain_headers:
    additional=json.loads(gzip.decompress(a.additional_toolchain_headers.read_bytes()))
    assert additional['schema']==1
    for row in additional['headers']:
        name=row['path']
        assert row['match'] and row['actual']==row['expected'] and name not in external
        label='ndk' if '/ndk/' in name else 'clang'
        keys=[key for key in docs if key.startswith('external:'+label+'/')]
        assert keys
        external[name]=(row['actual'],keys)
wrapper=(a.notices_root/'wrapper-licenses/arm64-v8a/LICENSE').read_bytes()
assert hashlib.sha256(wrapper).hexdigest()=='2f02b7486bcfa90d115c71a20437f3906b6fd5bef81c5dc0efd341399e89d0fd'
add('wrapper:cronet-go',wrapper,'Pinned cronet-go module LICENSE, identical across four ABI modules')
special_names={'src/buildtools/third_party/libc++/__assertion_handler','src/buildtools/third_party/libc++/__config_site','src/buildtools/third_party/libc++abi/cxa_demangle_stub.cc','src/third_party/googletest/src/googletest/include/gtest/gtest_prod.h'}
special={}
with tarfile.open(a.source_archive,'r:gz') as tar:
    for entry in tar:
        for name in special_names:
            if entry.name.endswith('/'+name):special[name]=tar.extractfile(entry).read()
assert len(special)==4
assert b'Apache License v2.0 with LLVM Exceptions' in special['src/buildtools/third_party/libc++/__assertion_handler']
assert special['src/buildtools/third_party/libc++/__config_site'].startswith(b'#ifndef _LIBCPP_CONFIG_SITE')
assert special['src/buildtools/third_party/libc++abi/cxa_demangle_stub.cc'].startswith(b'// Copyright 2018 The Chromium Authors')
gtest='src/third_party/googletest/src/googletest/include/gtest/gtest_prod.h'
data=special[gtest];guard=re.search(rb'(?m)^#ifndef\s+\w+',data);assert guard
notice=data[:guard.start()];assert b'Redistribution and use in source and binary forms' in notice and b'THIS SOFTWARE IS PROVIDED' in notice
add('header:googletest-prod',notice,gtest+' copyright/license prologue; exact original byte prefix')
metadata={str(PurePosixPath(r['path']).parent):r for r in inv['chromium_metadata']}
unknown=set();exceptions={}
def candidates(name):
    if name==gtest:return ['header:googletest-prod']
    if name=='src/buildtools/third_party/libc++/__assertion_handler':return ['source:src/third_party/libc++/src/LICENSE.TXT']
    if name=='src/buildtools/third_party/libc++/__config_site':
        exceptions[name]='No standalone notice declaration; retain both Chromium and libc++ notices conservatively'
        return ['source:src/LICENSE','source:src/third_party/libc++/src/LICENSE.TXT']
    if name=='src/buildtools/third_party/libc++abi/cxa_demangle_stub.cc':return ['source:src/LICENSE']
    for prefix,label in (('src/third_party/perfetto/','perfetto'),('src/third_party/protobuf/','protobuf'),('src/third_party/compiler-rt/','compiler-rt')):
        if name.startswith(prefix):return ['supplement:'+label]
    parent=PurePosixPath(name).parent
    meta=next((metadata[str(p)] for p in (parent,*parent.parents) if str(p) in metadata),None)
    declared=sorted({'source:'+r['resolved'] for d in meta['dependencies'] for r in d['declared_license_files'] if r.get('present')}) if meta else []
    if declared:return declared
    if '/third_party/' not in name:return ['source:src/LICENSE']
    unknown.add(name);return []
producer_notices={}
def producer_candidates(label,chain=()):
    if label in chain:raise ValueError('Producer cycle')
    if label in producer_notices:return producer_notices[label]
    result=set()
    for ref in generated['producers'][label]['declaredInputOrigins']:
        if ref['kind']=='pinned_git':result.update(candidates('src/'+ref['path'][2:]))
        elif ref['kind']=='generated':result.update(producer_candidates(ref['producer'],(*chain,label)))
        elif ref['kind']=='built_tool':
            for source in generated['builtTools'][ref['producer']]['sources']:
                if source['gitBlob']:result.update(candidates('src/'+source['path'][2:]))
                else:unknown.add(source['path'])
        else:unknown.add(ref['path'])
    producer_notices[label]=sorted(result);return producer_notices[label]
gen_files={r['path']:r for r in generated['files']};coverage=[]
for row in mapping['files']:
    path=row['sourceRelativePath']
    if row['status']=='pinned_git_bytes_match':refs=candidates('src/'+path)
    elif row['status']=='generated_not_in_git':
        assert gen_files[path]['observedSha256']==row['sha256']
        refs=producer_candidates(gen_files[path]['producer'])
    elif path in external:
        sha,refs=external[path];assert sha==row['sha256']
    else:unknown.add(path);refs=[]
    assert all(ref in docs for ref in refs)
    coverage.append(dict(path=path,observedSha256=row['sha256'],noticeCandidates=refs))
assert not unknown,sorted(unknown)
assert all(row['noticeCandidates'] for row in coverage)
assert len(docs)<=128 and sum(len(d['data']) for d in docs.values())<=32*1024*1024
out.mkdir(parents=True);(out/'documents').mkdir()
index=[]
with (out/'NOTICE-CANDIDATE.txt').open('wb') as combined:
    combined.write(b'Cronet full-source notice candidate. Includes source/build-tool documents conservatively. Not a final shipped-only NOTICE or legal compliance conclusion.\n')
    for i,(key,row) in enumerate(sorted(docs.items()),1):
        name=f'doc-{i:03d}.txt';(out/'documents'/name).write_bytes(row['data'])
        combined.write(('\n\n===== '+key+' =====\n').encode()+row['data']+b'\n')
        index.append(dict(id=key,file='documents/'+name,sha256=row['sha256'],bytes=len(row['data']),origin=row['origin']))
report=dict(schema=1,compilerRun=mapping['compilerRun'],abi=mapping['abi'],inputPaths=len(coverage),documents=index,coverage=coverage,producerNoticeCandidates=producer_notices,unresolvedPaths=[],conservativeExceptions=exceptions,limits=['Full-source conservative document collection, not a final shipped-only legal conclusion','Generated candidates follow declared data/scripts and built-tool entry sources, not all transitive generator runtime code','Other ABI byte/header inventories remain separate'])
(out/'inventory.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(dict(inputPaths=len(coverage),noticeDocuments=len(docs),generatedProducers=len(producer_notices),unresolvedPaths=len(unknown),conservativeExceptions=exceptions)))
