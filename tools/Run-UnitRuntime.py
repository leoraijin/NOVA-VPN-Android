"""Run the complete exported Gradle JUnit runtime without Windows @argfile decoding.
No Gradle/cache patching; compilation/export must have succeeded first.
"""
import argparse, hashlib, json, pathlib, re, subprocess, zipfile
p=argparse.ArgumentParser()
p.add_argument('--runtime',required=True); p.add_argument('--java',required=True); p.add_argument('--output-directory',required=True)
a=p.parse_args(); repo=pathlib.Path(__file__).resolve().parents[1]; project=repo.parent
metadata=pathlib.Path(a.runtime).resolve(); out=pathlib.Path(a.output_directory).resolve()
if not metadata.is_relative_to(project) or not out.is_relative_to(project): raise SystemExit('Runtime and fresh evidence must stay inside this project')
if out.exists(): raise SystemExit('Fresh output directory required')
data=json.loads(metadata.read_text(encoding='utf8')); assert data['schema']==1 and data['task'] in (':app:testRealitypreviewUnitTest', ':app:testOptimizedUnitTest')
for source in (repo/'app/src').rglob('*'):
 if source.is_file() and source.stat().st_mtime_ns>metadata.stat().st_mtime_ns: raise SystemExit('Sources changed after Gradle runtime export; compile/export again')
classes=[]
for directory in data['classes']:
 root=pathlib.Path(directory).resolve()
 if not root.is_relative_to(repo/'app/build'): raise SystemExit('Foreign test classes refused')
 for binary in sorted(root.rglob('*Test.class')):
  if '$' not in binary.name: classes.append('.'.join(binary.relative_to(root).with_suffix('').parts))
assert classes and len(set(classes))==len(classes)
source_classes=set()
for source in (repo/'app/src/test').rglob('*Test.kt'):
 package=re.search(r'^package\s+([\w.]+)',source.read_text(encoding='utf8'),re.MULTILINE)
 if not package: raise SystemExit('Test package missing: '+str(source))
 source_classes.add(package.group(1)+'.'+source.stem)
if set(classes)!=source_classes: raise SystemExit('Compiled/source test inventory mismatch; compile current complete test set')
urls=[]
for entry in data['classpath']:
 entry=pathlib.Path(entry).resolve()
 if entry.exists(): urls.append(entry.as_uri()+('/' if entry.is_dir() else ''))
out.mkdir(parents=True)
# Manifest bytes stay ASCII; Unicode file names are UTF-8 percent-encoded URIs.
# Continuation records are <=72 bytes, as required by the JAR specification.
logical='Class-Path: '+' '.join(urls); lines=[]
while logical:
 width=72 if not lines else 71
 lines.append(('' if not lines else ' ')+logical[:width]); logical=logical[width:]
manifest=('Manifest-Version: 1.0\r\n'+'\r\n'.join(lines)+'\r\n\r\n').encode('ascii')
jar=out/'runtime-classpath.jar'
with zipfile.ZipFile(jar,'x') as archive: archive.writestr('META-INF/MANIFEST.MF',manifest)
props=data['systemProperties']; pathlib.Path(props['user.home']).mkdir(parents=True,exist_ok=True)
command=[str(pathlib.Path(a.java).resolve()),'-Xmx2g','-ea','-Dfile.encoding=UTF-8',*data['jvmArgs'],*[f'-D{k}={v}' for k,v in props.items()],'-cp',str(jar),'org.junit.runner.JUnitCore',*classes]
record={'schema':1,'runner':'JUnitCore; complete exported Gradle runtime via URI manifest','gradleTestTaskExecuted':False,'runtimeSha256':hashlib.sha256(metadata.read_bytes()).hexdigest(),'classpathEntries':len(urls),'classes':classes,'java':command[0], 'productSourceSha256':{str(f.relative_to(repo)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((repo/'app/src').rglob('*')) if f.is_file()}, 'runnerSha256':hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest()}
(out/'run.json').write_text(json.dumps(record,indent=2),encoding='utf8')
with (out/'junit.log').open('wb') as log:
 child=subprocess.Popen(command,cwd=data['workingDirectory'],stdout=log,stderr=subprocess.STDOUT)
 print(f'JUnit process {child.pid}: {len(classes)} test classes',flush=True)
 record['pid']=child.pid; (out/'run.json').write_text(json.dumps(record,indent=2),encoding='utf8')
 record['exit']=child.wait()
(out/'run.json').write_text(json.dumps(record,indent=2),encoding='utf8')
print((out/'junit.log').read_text(encoding='utf8',errors='replace')[-6500:],flush=True)
raise SystemExit(record['exit'])