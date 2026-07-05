package com.cncd.ch04.client;
import java.awt.Color;
import java.io.*;
/**
 * 主题色板(功能十四):全部界面颜色的唯一出处。
 * 消息 HTML 一律用 %TOKEN% 占位符写颜色,渲染时按当前主题替换——
 * 因此切换日间/夜间时,对历史消息整体重渲染即可换色,无需改动任何消息内容。
 * 用户偏好落盘 ~/.chattool-theme,下次启动记忆。
 */
public class Theme {
    public static final String PRIV = "%PRIV%";  // 私聊/文件 紫
    public static final String WARN = "%WARN%";  // 好友上线等提醒 橙
    public static final String ERR  = "%ERR%";   // 错误/断线 红
    public static final String OK   = "%OK%";    // 成功/连接 绿
    public static final String SELF = "%SELF%";  // 自己的消息 灰
    public static final String TIME = "%TIME%";  // 时间戳 浅灰
    private static boolean dark = load();
    // 气泡界面(功能二十七)配色占位符
    public static final String CHATBG   = "%CHATBG%";   // 聊天区背景(衬托气泡)
    public static final String SELFBUB  = "%SELFBUB%";  // 自己的气泡(仿微信绿)
    public static final String OTHERBUB = "%OTHERBUB%"; // 对方的气泡
    public static final String BUBTX    = "%BUBTX%";    // 气泡内文字
    public static boolean isDark() { return dark; }
    public static void toggle() { dark = !dark; save(); }
    public static String bg() { return dark ? "#1e1f22" : "#ffffff"; }
    public static String fg() { return dark ? "#e8e8e8" : "#1c1e21"; }
    public static Color bgColor()      { return Color.decode(bg()); }
    public static Color fgColor()      { return Color.decode(fg()); }
    public static Color panelColor()   { return dark ? Color.decode("#2b2d31") : Color.decode("#eef0f4"); }
    public static String chatBg()      { return dark ? "#26272b" : "#f3f4f6"; } // 微信式浅灰聊天背景
    public static Color chatBgColor()  { return Color.decode(chatBg()); }
    public static Color accentColor()  { return dark ? Color.decode("#3e5a4a") : Color.decode("#07c160"); } // 微信绿(气泡用)
    public static Color borderColor()  { return dark ? Color.decode("#3a3c40") : Color.decode("#dcdfe4"); } // 柔和分隔线
    /** 把 HTML 中的颜色占位符替换为当前主题的实际色值 */
    public static String apply(String html) {
        return html.replace(PRIV, dark ? "#c9a3f0" : "#7c3aed")
                   .replace(WARN, dark ? "#ffb357" : "#e8730c")
                   .replace(ERR,  dark ? "#ff7b72" : "#d93025")
                   .replace(OK,   dark ? "#7ee787" : "#1a7f37")
                   .replace(SELF, dark ? "#9aa0a6" : "#8a9099")
                   .replace(TIME, dark ? "#707070" : "#9aa0a6")
                   .replace(CHATBG,   chatBg())
                   .replace(SELFBUB,  dark ? "#2e4d3a" : "#95ec69")
                   .replace(OTHERBUB, dark ? "#3a3b3f" : "#ffffff")
                   .replace(BUBTX,    dark ? "#e8e8e8" : "#1c1e21");
    }
    private static File prefFile() { return new File(System.getProperty("user.home"), ".chattool-theme"); }
    private static boolean load() {
        try {
            BufferedReader r = new BufferedReader(new FileReader(prefFile()));
            String s = r.readLine();
            r.close();
            return "dark".equals(s);
        } catch(Exception e) { return false; }
    }
    private static void save() {
        try {
            FileWriter w = new FileWriter(prefFile());
            w.write(dark ? "dark" : "light");
            w.close();
        } catch(Exception e) {}
    }
}
