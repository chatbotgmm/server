"""Local JVM + source contracts; does not build Android or contact a backend."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ANDROID = Path(__file__).resolve().parents[1]
JAVA = ANDROID / 'app/src/main/java/org/dpdns/gmm'

class BackendTargetTests(unittest.TestCase):
    def test_actual_java_namespace_and_redirect_contract(self):
        harness = r'''package org.dpdns.gmm;
import java.net.*;
public final class NativeBackendContractCheck {
 public static void main(String[] args) throws Exception {
  String prod="https://gmm.dpdns.org";
  if(!NativeBackend.accountPreferences(prod).equals("native_account"))throw new AssertionError("production compatibility");
  java.util.Set<String> names=new java.util.HashSet<>();
  for(String target:new String[]{"https://test.example","https://other.example","https://test.example:8443","http://127.0.0.1:9","http://10.0.2.2:9","https://gmm.dpdns.org/","https://GMM.dpdns.org"}){
   String name=NativeBackend.accountPreferences(target);
   if(name.equals("native_account")||!name.matches("native_account_backend_[0-9a-f]{64}")||!names.add(name)||!name.equals(NativeBackend.accountPreferences(target)))throw new AssertionError("backend isolation: "+target);
  }
  for(int code:new int[]{200,204,299,400,401,500})NativeBackend.requireDirectResponse(code);
  for(int code:new int[]{300,301,302,303,304,307,308,399}){
   boolean rejected=false;try{NativeBackend.requireDirectResponse(code);}catch(java.io.IOException expected){rejected=true;}
   if(!rejected)throw new AssertionError("redirect: "+code);
  }
  HttpURLConnection connection=(HttpURLConnection)new URL("https://example.invalid").openConnection();
  NativeBackend.directConnection(connection);
  if(connection.getInstanceFollowRedirects())throw new AssertionError("redirect flag");
  connection.disconnect(); // No connect/getResponseCode/network call.
  boolean rejected=false;try{NativeBackend.accountPreferences(null);}catch(IllegalArgumentException expected){rejected=true;}
  if(!rejected)throw new AssertionError("null backend");
  System.out.println("Actual NativeBackend JVM contract PASS; no network requests");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='gmm-backend-java-') as folder:
            root=Path(folder); check=root/'NativeBackendContractCheck.java'; check.write_text(harness)
            subprocess.run(['java','com.sun.tools.javac.Main','-source','17','-target','17','-d',str(root),str(JAVA/'NativeBackend.java'),str(check)],check=True,capture_output=True,text=True)
            result=subprocess.run(['java','-cp',str(root),'org.dpdns.gmm.NativeBackendContractCheck'],check=True,capture_output=True,text=True)
            self.assertIn('contract PASS',result.stdout)
    def test_every_account_store_uses_selected_backend(self):
        for source in JAVA.glob('*.java'):
            self.assertNotIn('getSharedPreferences("native_account",',source.read_text(),source.name)
        for name in ['NativeApi','MainActivity','NativeExtras','NativeAlerts','NativeSettingsSheet']:
            self.assertIn('getSharedPreferences(NativeBackend.accountPreferences(BuildConfig.SERVER_URL),',(JAVA/(name+'.java')).read_text())
        helper=(JAVA/'NativeBackend.java').read_text()
        self.assertNotIn('SharedPreferences',helper) # pure naming only; no copying or erasing token stores.
    def test_authenticated_connections_reject_redirects_before_reading(self):
        for name,var in [('NativeApi','conn'),('NativeArt','c')]:
            source=(JAVA/(name+'.java')).read_text()
            direct=source.index(f'NativeBackend.directConnection({var});')
            self.assertLess(direct,source.index(f'{var}.setRequestProperty("x-gmm-s"'))
            reject=source.index('NativeBackend.requireDirectResponse(')
            self.assertLess(reject,source.index(f'{var}.getInputStream()'))
    def test_build_property_is_explicit_for_every_variant(self):
        source=(ANDROID/'app/build.gradle').read_text()
        self.assertIn("project.findProperty('gmmServerUrl')",source)
        self.assertIn("project.findProperty('nativeServer')",source)
        self.assertIn('buildConfigField "String", "SERVER_URL", releaseServerLiteral',source)
        self.assertIn('buildConfigField "String", "SERVER_URL", debugServerLiteral',source)
        self.assertIn("groovy.json.JsonOutput.toJson(raw)",source)

if __name__=='__main__':unittest.main()
