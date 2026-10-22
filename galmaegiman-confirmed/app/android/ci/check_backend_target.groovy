// Run with Groovy 3.x + groovy-json; no Gradle, Android SDK or network is needed.
import groovy.json.JsonSlurper
String source = new File(args[0]).getText('UTF-8')
String block = source.split('// BEGIN REVIEWED BACKEND TARGET', 2)[1].split('// END REVIEWED BACKEND TARGET', 2)[0]
block = block.substring(block.indexOf('\n') + 1)
String exception = 'class GradleException extends RuntimeException { GradleException(String s) {super(s)}; GradleException(String s, Throwable t) {super(s,t)} }\n'
def resolve = { Map props ->
    new GroovyShell(new Binding([project: [findProperty: { String name -> props[name] }]])).evaluate(exception + block + '\n[release: releaseServerLiteral, debug: debugServerLiteral]')
}
int checks = 0
assert new JsonSlurper().parseText(resolve([:]).release) == 'https://gmm.dpdns.org'; checks++
for (String url : ['https://test.example', 'https://test.example:8443', 'https://[::1]:443']) {
    def result = resolve([gmmServerUrl:url]); assert new JsonSlurper().parseText(result.release)==url; assert result.debug==result.release; checks++
}
for (String host : ['localhost','127.0.0.1','[::1]','10.0.2.2']) {
    String url = 'http://' + host + ':9'; def result = resolve([nativeServer:url]);
    assert new JsonSlurper().parseText(result.debug)==url
    assert new JsonSlurper().parseText(result.release)=='https://gmm.dpdns.org'; checks++
}
for (String url : ['', 'http://test.example', 'http://127.0.0.1:9', 'https://u:p@test.example', 'https://test.example?', 'https://test.example#', 'https://test.example/path', 'https://test.example/', 'https://test.example:0', 'https://test.example:65536', 'https://test.example\n', 'https://test.example\t', 'https://test.example"', 'https://test.example\\evil', 'file:///tmp/x', 'https://tést.example', 'https://test.example\u007f']) {
    boolean rejected = false; try { resolve([gmmServerUrl:url]) } catch (Exception expected) { rejected=true }; assert rejected : url; checks++
}
for (String url : ['http://example.com', 'http://localhost.evil', 'http://127.0.0.2', 'http://2130706433', 'http://127.0.0.1@evil.example', 'http://[::2]']) {
    boolean rejected=false; try {resolve([nativeServer:url])} catch(Exception expected){rejected=true}; assert rejected:url; checks++
}
boolean conflict=false; try {resolve([gmmServerUrl:'https://one.example',nativeServer:'https://two.example'])} catch(Exception expected){conflict=true}; assert conflict; checks++
assert resolve([gmmServerUrl:'https://same.example',nativeServer:'https://same.example']).debug=='"https://same.example"'; checks++
println "Backend target Groovy validation: ${checks} cases PASS"
