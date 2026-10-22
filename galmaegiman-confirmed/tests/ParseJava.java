import javax.tools.*;import com.sun.source.util.JavacTask;import java.util.*;
class ParseJava {public static void main(String[] a)throws Exception{
JavaCompiler c=ToolProvider.getSystemJavaCompiler(); DiagnosticCollector<JavaFileObject> d=new DiagnosticCollector<>();
try(StandardJavaFileManager f=c.getStandardFileManager(d,null,null)){
JavacTask task=(JavacTask)c.getTask(null,f,d,List.of("-proc:none"),null,f.getJavaFileObjects(a));task.parse();
for(Diagnostic<?> x:d.getDiagnostics())if(x.getKind()==Diagnostic.Kind.ERROR)throw new AssertionError(x.toString());
System.out.println("PASS: Java syntax only (Android symbols not compiled), "+a.length+" files");}}}
