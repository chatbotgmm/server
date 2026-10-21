import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** JDK 17 이상, headless 환경에서 실행하는 APK 파생 자산 생성기. */
public final class PrepareUpper33Art {
  public static void main(String[] args) throws Exception {
    if(args.length!=4)throw new IllegalArgumentException("source manifest catalog output");
    Path source=Path.of(args[0]), manifest=Path.of(args[1]), catalog=Path.of(args[2]), output=Path.of(args[3]);
    java.util.List<String> lines=Files.readAllLines(manifest,StandardCharsets.UTF_8);
    if(lines.size()!=264)throw new IOException("264 sheets required");
    Set<String> paths=new HashSet<>(); long total=0;
    for(String line:lines){
      String[] entry=line.split("\\t",-1);
      if(entry.length!=2||!entry[0].matches("(?:D[1-8]|T[1-8]|E[1-8]|I[1-9])/(?:front|back)/(?:idle|entry|attack|hit-defend)\\.png")
          ||!entry[1].matches("[a-f0-9]{64}")||!paths.add(entry[0]))throw new IOException("Invalid manifest entry");
      Path file=source.resolve(entry[0]); MessageDigest digest=MessageDigest.getInstance("SHA-256");
      try(InputStream in=Files.newInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}
      if(!HexFormat.of().formatHex(digest.digest()).equals(entry[1]))throw new IOException("Hash mismatch: "+file);
      BufferedImage original=ImageIO.read(file.toFile());
      if(original==null||original.getWidth()!=1536||original.getHeight()!=1024)throw new IOException("Invalid source size: "+file);
      BufferedImage small=new BufferedImage(768,512,BufferedImage.TYPE_INT_ARGB);
      Graphics2D g=small.createGraphics();
      try{g.setComposite(AlphaComposite.Src);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        // 셀 경계에서 이웃 프레임 색이 bicubic 보간에 섞이지 않도록 따로 축소한다.
        for(int row=0;row<2;row++)for(int col=0;col<3;col++){
          BufferedImage cell=original.getSubimage(col*512,row*512,512,512);
          g.drawImage(cell,col*256,row*256,256,256,null);
        }
      }finally{g.dispose();original.flush();}
      Path target=output.resolve(entry[0]);Files.createDirectories(target.getParent());
      if(!ImageIO.write(small,"png",target.toFile()))throw new IOException("PNG writer unavailable");
      small.flush();total+=Files.size(target);
    }
    Files.copy(catalog,output.resolve("catalog.json"),StandardCopyOption.REPLACE_EXISTING);
    System.out.println("상위 33종: 264개 축소 PNG, "+total+" bytes (카탈로그 제외)");
  }
}
