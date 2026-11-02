"""Trace observed generated compiler inputs to exact GN producers and declared inputs."""
import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--source-mapping', required=True, type=Path)
parser.add_argument('--graph', required=True, type=Path)
parser.add_argument('--git-tree', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
mapping = json.loads(gzip.decompress(args.source_mapping.read_bytes()))
graph = json.loads(args.graph.read_text())
tree = json.loads(args.git_tree.read_text())
assert tree['sha'] == mapping['sourceCommit'] == '72a06c9fca0e2d228588c7f3074bf7efff3ff686' and not tree['truncated']
blobs = {r['path']: r['sha'] for r in tree['tree'] if r['type'] == 'blob'}
outputs = defaultdict(list)
for name, target in graph.items():
    for output in target.get('outputs', []):
        outputs[output].append(name)
producers, tools, unknown = {}, {}, set()


def source_ref(path, chain=()):
    assert isinstance(path, str) and path.startswith('//'), path
    if 'src/' + path[2:] in blobs:
        return dict(kind='pinned_git', path=path, gitBlob=blobs['src/' + path[2:]])
    owners = outputs.get(path, [])
    if len(owners) != 1:
        unknown.add(path)
        return dict(kind='unresolved', path=path, possibleProducers=owners)
    owner = owners[0]
    target = graph[owner]
    if target['type'] in ('action', 'action_foreach', 'copy', 'generated_file'):
        ensure_producer(owner, chain)
        return dict(kind='generated', path=path, producer=owner)
    if target['type'] in ('executable', 'shared_library', 'static_library', 'source_set'):
        if owner not in tools:
            tools[owner] = dict(type=target['type'], toolchain=target['toolchain'], dependencies=target.get('deps', []),
                sources=[dict(path=p, gitBlob=blobs.get('src/' + p[2:])) for p in target.get('sources', [])])
        return dict(kind='built_tool', path=path, producer=owner)
    unknown.add(path)
    return dict(kind='unresolved', path=path, possibleProducers=owners)


def ensure_producer(name, chain):
    if name in chain:
        raise ValueError('Generated input cycle: ' + name)
    if name in producers:
        return
    target = graph[name]
    paths = sorted(set(target.get('sources', []) + target.get('inputs', []) + ([target['script']] if target.get('script') else [])))
    producer = dict(type=target['type'], toolchain=target['toolchain'], outputs=target.get('outputs', []),
                    script=target.get('script'), args=target.get('args', []),
                    responseFileContents=target.get('response_file_contents', []), dependencies=target.get('deps', []))
    producer['declaredInputOrigins'] = [source_ref(path, (*chain, name)) for path in paths]
    producers[name] = producer


files = []
for row in mapping['files']:
    if row['status'] != 'generated_not_in_git':
        continue
    reference = source_ref('//' + row['sourceRelativePath'])
    assert reference['kind'] == 'generated', row['sourceRelativePath']
    files.append(dict(path=row['sourceRelativePath'], observedSha256=row['sha256'], producer=reference['producer']))
report = dict(schema=1, abi=mapping['abi'], compilerRun=mapping['compilerRun'], sourceCommit=tree['sha'],
              sourceMappingSha256=hashlib.sha256(args.source_mapping.read_bytes()).hexdigest(),
              graphSha256=hashlib.sha256(args.graph.read_bytes()).hexdigest(), generatedDependencyPaths=len(files),
              producerTypes=dict(Counter(v['type'] for v in producers.values())), unresolvedDeclaredInputs=sorted(unknown),
              files=files, producers=producers, builtTools=tools,
              scope='Exact GN producer ownership and declared input provenance for observed generated inputs. Generator source/runtime transitive closure and final notice associations are not inferred complete.')
with args.output.open('x', encoding='utf-8') as stream:
    json.dump(report, stream, indent=2)
    stream.write('\n')
print(json.dumps({k:report[k] for k in ('generatedDependencyPaths','producerTypes','unresolvedDeclaredInputs')}))
print('Producer nodes:',len(producers),'built tools:',len(tools))
