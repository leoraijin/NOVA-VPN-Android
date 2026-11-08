"""Build isolated Java-only companion; no Gradle, network, release keys or phone changes."""
import argparse,hashlib,json,pathlib,re,subprocess,xml.etree.ElementTree as ET,zipfile
p=argparse.ArgumentParser();p.add_argument('--toolchain',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);p.add_argument('--shared-uid-pair',action='store_true');a=p.parse_args()
repo=pathlib.Path(__file__).resolve().parents[1];out=a.output.resolve();work=(repo/'work').resolve()
assert out.is_relative_to(work) and not out.exists(),'Fresh project work output required'
out.mkdir();classes=out/'classes';classes.mkdir();dex=out/'dex';dex.mkdir()
t=a.toolchain.resolve();jdk=t/'jdk17/jdk-17.0.20.1+1/bin';bt=t/'android-sdk/build-tools/35.0.0';android=t/'android-sdk/platforms/android-35/android.jar';src=repo/'tools/routing-probe'
logs=[]
def run(args):
 r=subprocess.run([str(x) for x in args],capture_output=True,timeout=60)
 logs.append({'command':[str(x) for x in args],'exit':r.returncode,'output':r.stdout.decode(errors='replace')+r.stderr.decode(errors='replace')})
 (out/'commands.json').write_text(json.dumps(logs,indent=2),encoding='utf8');assert r.returncode==0,logs[-1]['output'];return r.stdout.decode(errors='replace')
txt_probe=repo/'app/src/androidTest/java/com/nova/vpn/android/RoutingTxtProbe.java'
run([jdk/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',classes,*src.rglob('*.java'),txt_probe])
run([jdk/'java.exe','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','29','--output',dex,*classes.rglob('*.class')])
key=work/'routing-probe-test-signing.jks'
if not key.exists():run([jdk/'keytool.exe','-genkeypair','-keystore',key,'-alias','routing-probe','-storepass','android','-keypass','android','-dname','CN=NOVA isolated test only','-keyalg','RSA','-keysize','2048','-validity','3650','-storetype','JKS','-noprompt'])

sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
probe_package='com.nova.vpn.routingprobe'
shared_uid=probe_package+'.shared.uid'

def build_probe(package, manifest, directory, file_name):
 base=directory/'base.apk'
 run([bt/'aapt2.exe','link','-I',android,'--manifest',manifest,'-o',base])
 with zipfile.ZipFile(base,'a') as z:z.write(dex/'classes.dex','classes.dex')
 aligned=directory/'aligned.apk'
 run([bt/'zipalign.exe','-f','4',base,aligned])
 apk=directory/file_name
 run([jdk/'java.exe','-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','routing-probe','--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned])
 verify=run([jdk/'java.exe','-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',apk])
 digest=re.search(r'^Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})\r?$',verify,re.MULTILINE)
 assert digest,'Expected a test signer'
 badging=run([bt/'aapt2.exe','dump','badging',apk])
 assert f"package: name='{package}'" in badging
 xmltree=run([bt/'aapt2.exe','dump','xmltree','--file','AndroidManifest.xml',apk])
 assert ('sharedUserId' in xmltree)==a.shared_uid_pair
 if a.shared_uid_pair:
  assert any('sharedUserId' in line and f'"{shared_uid}"' in line for line in xmltree.splitlines())
 with zipfile.ZipFile(apk) as signed:assert signed.testzip() is None
 print('Signed companion verified:',apk,'bytes',apk.stat().st_size)
 return {'package':package,'apk':apk.relative_to(out).as_posix(),'apkSha256':sha(apk),'signerSha256':digest[1].lower()}

manifest=src/'AndroidManifest.xml'
receipt={'passed':True,'sources':{str(f.relative_to(repo)):sha(f) for f in (*src.rglob('*'),txt_probe) if f.is_file()},
         'scriptSha256':sha(pathlib.Path(__file__)),'scope':'Standalone signed test companion, not installed or runtime-tested'}
if a.shared_uid_pair:
 artifacts=[]
 for label in ('first','second'):
  directory=out/label;directory.mkdir()
  package=probe_package+'.shared.'+label
  tree=ET.parse(manifest);root=tree.getroot()
  root.set('package',package);root.set('{http://schemas.android.com/apk/res/android}sharedUserId',shared_uid)
  variant=directory/'AndroidManifest.xml'
  tree.write(variant,encoding='utf-8',xml_declaration=True)
  artifacts.append(build_probe(package,variant,directory,'nova-routing-probe-'+label+'.apk'))
 assert len({artifact['signerSha256'] for artifact in artifacts})==1
 assert len({artifact['apkSha256'] for artifact in artifacts})==2
 receipt.update(sharedUserId=shared_uid,artifacts=artifacts,
                scope='Signed shared-UID probe pair; actual PackageManager UID equality requires device installation')
else:
 normal=build_probe(probe_package,manifest,out,'nova-routing-probe.apk')
 receipt.update(package=normal['package'],apkSha256=normal['apkSha256'])
(out/'verified-result.json').write_text(json.dumps(receipt,indent=2),encoding='utf8')
