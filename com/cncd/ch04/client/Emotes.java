package com.cncd.ch04.client;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
/**
 * 表情系统(功能二十六)。
 * 内置表情由代码绘制生成到 ~/.chattool-emotes/,每个客户端首次运行各自生成同一套,
 * 因此聊天里只需传输短码文本(如 /微笑),接收端本地渲染成图——零流量的图片表情。
 * 自定义表情放 custom 子目录,点击即作为图片文件发送(复用分块传输,对端自动缓存显示)。
 */
public class Emotes {
    public static final String[] NAMES = {"微笑","大笑","哭","生气","酷","惊讶","爱心","赞"};
    static final File DIR = new File(System.getProperty("user.home"), ".chattool-emotes");
    static final File CUSTOM = new File(DIR, "custom");
    static { ensure(); }
    public static File file(String name) { return new File(DIR, name + ".png"); }
    // 把正文中的 /短码 替换为内嵌小图(20px);未知短码原样保留
    public static String apply(String body) {
        for(int i = 0; i < NAMES.length; i++) {
            String code = "/" + NAMES[i];
            if(body.indexOf(code) >= 0) {
                File f = file(NAMES[i]);
                body = body.replace(code,
                    "<img src=\"" + f.toURI() + "\" width=\"20\" height=\"20\">");
            }
        }
        return body;
    }
    // 首次运行绘制生成内置表情
    static void ensure() {
        DIR.mkdirs();
        CUSTOM.mkdirs();
        for(int i = 0; i < NAMES.length; i++) {
            File f = file(NAMES[i]);
            if(!f.exists()) {
                try { javax.imageio.ImageIO.write(draw(NAMES[i]), "png", f); }
                catch(Exception e) {}
            }
        }
    }
    static BufferedImage draw(String name) {
        BufferedImage im = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if(name.equals("爱心")) {
            g.setColor(new Color(0xe0, 0x30, 0x50));
            g.fillOval(3, 4, 13, 13);
            g.fillOval(16, 4, 13, 13);
            int[] xs = {3, 29, 16};
            int[] ys = {12, 12, 30};
            g.fillPolygon(xs, ys, 3);
        } else if(name.equals("赞")) {
            g.setColor(new Color(0xf5, 0xa6, 0x23));
            g.fillRect(4, 16, 6, 13);                 // 手掌
            g.fillRoundRect(10, 12, 17, 17, 6, 6);    // 手背
            g.fillRoundRect(12, 2, 6, 14, 6, 6);      // 大拇指
        } else { // 圆脸系列
            g.setColor(new Color(0xff, 0xd5, 0x3e));
            g.fillOval(1, 1, 30, 30);
            g.setColor(new Color(0x66, 0x44, 0x00));
            if(name.equals("酷")) { // 墨镜
                g.fillRect(5, 10, 9, 6);
                g.fillRect(18, 10, 9, 6);
                g.drawLine(14, 12, 18, 12);
            } else if(name.equals("哭")) {
                g.fillOval(8, 10, 4, 4);
                g.fillOval(20, 10, 4, 4);
                g.setColor(new Color(0x33, 0x99, 0xff)); // 眼泪
                g.fillOval(7, 15, 4, 8);
            } else if(name.equals("惊讶")) {
                g.fillOval(8, 9, 5, 5);
                g.fillOval(19, 9, 5, 5);
            } else {
                g.fillOval(8, 10, 4, 4);
                g.fillOval(20, 10, 4, 4);
            }
            if(name.equals("生气")) { // 皱眉
                g.drawLine(6, 8, 12, 11);
                g.drawLine(26, 8, 20, 11);
            }
            // 嘴
            if(name.equals("微笑")) g.drawArc(9, 14, 14, 10, 200, 140);
            else if(name.equals("大笑")) { g.fillArc(8, 15, 16, 11, 180, 180); }
            else if(name.equals("哭")) g.drawArc(10, 21, 12, 7, 20, 140);
            else if(name.equals("生气")) g.drawArc(10, 21, 12, 7, 20, 140);
            else if(name.equals("酷")) g.drawLine(11, 23, 21, 23);
            else if(name.equals("惊讶")) g.drawOval(13, 19, 7, 8);
        }
        g.dispose();
        return im;
    }
    // 自定义表情文件列表
    public static File[] customs() {
        File[] fs = CUSTOM.listFiles();
        return fs == null ? new File[0] : fs;
    }
    // 把一张图片收藏为自定义表情:缩放到 ≤96px 的小 PNG 存入 custom 目录。
    // 缩小的意义:①面板加载快(读小图);②发送时体积只有几 KB,近乎瞬发;
    // ③文件名统一加 emote_ 前缀,发送/接收端据此把它渲染成内嵌小表情而非"[图片]"大图。
    public static void addCustom(File img) throws Exception {
        BufferedImage src = javax.imageio.ImageIO.read(img);
        if(src == null) throw new Exception("不是有效的图片");
        int max = Math.max(src.getWidth(), src.getHeight());
        double sc = Math.min(1.0, 128.0 / max);
        int w = Math.max(1, (int)(src.getWidth() * sc));
        int h = Math.max(1, (int)(src.getHeight() * sc));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        javax.imageio.ImageIO.write(out, "png",
            new File(CUSTOM, "emote_" + System.currentTimeMillis() + ".png"));
    }
    // 删除一个自定义表情文件(功能二十六:管理自己上传的表情)
    public static void deleteCustom(File f) {
        try { if(f != null && f.getParentFile().equals(CUSTOM)) f.delete(); } catch(Exception e) {}
    }
    // 是否为自定义表情文件名(收发端据此渲染成内嵌小表情)
    public static boolean isEmoteName(String fname) {
        return fname != null && fname.startsWith("emote_");
    }
}
