package com.cncd.ch04.client;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
public class ChatClient extends JFrame implements KeyListener, ActionListener, FocusListener {
    public static final String appName = "Chat Tool";
    public static final String serverText = "127.0.0.1";
    public static final String portText = "3500";
    public static final String nickText = "YourName";
    JPanel northPanel, southPanel, centerPanel;
    JTextField txtHost, txtPort, msgWindow, txtNick;
    JButton buttonConnect, buttonSend, buttonFile;
    JScrollPane sc;
    JList userList;
    DefaultListModel userModel;
    // ===== 会话式界面（仿 QQ）：左侧会话列表，中部按会话切换聊天窗格，右侧随会话显示在线用户/对方信息 =====
    static final String MAIN_ROOM = "公共聊天室";
    JList convList;
    DefaultListModel convModel;
    JPanel centerCards, eastCards;
    CardLayout centerLayout, eastLayout;
    JEditorPane infoPane;
    JButton buttonAddFriend;
    HashMap convs = new HashMap();     // 会话名 -> 聊天窗格
    Vector itemNames = new Vector();   // 左侧列表行号 -> 会话名（表头行为 null）
    Vector friends = new Vector();     // 我的好友（服务器 FRIENDS 推送）
    Vector online = new Vector();      // 在线用户（服务器 USERLIST 推送）
    Vector tempConvs = new Vector();   // 陌生人临时会话
    HashSet unread = new HashSet();    // 有未读消息的会话
    String currentConv = MAIN_ROOM;
    JButton buttonScan, buttonTheme;   // 扫描局域网(功能十)、日夜主题切换(功能十四)
    JPanel myInfoRows;                 // "我的信息"编辑区:行=(字段,内容)一对输入框
    Vector infoFieldRows = new Vector(); // 元素为 JTextField[2]
    JButton buttonAddRow, buttonSaveInfo;
    volatile boolean reconnecting = false; // 自动重连中(功能十一)
    Vector pendingOut = new Vector();  // 断线期间的待发消息,重连后补发
    String lastPassword;               // 缓存最近一次 /register 或 /verify 的密码,重连自动验证身份用
    HashMap recvs = new HashMap();     // 分块接收中的文件:tid -> FileRecv(功能十二)
    private static int tidCounter = 0;
    private String rawInfoHtml = "";   // 右栏信息的原始(带颜色占位符)HTML,切主题时重渲染
    ClientKernel ck;
    ClientHistory historyWindow;
    private String lastMsg = "";
    /** Creates a new instance of Class */
    public ChatClient() {
        uiInit();
        txtHost.setText("127.0.0.1");
        txtPort.setText("3500");
    }
    public void uiInit() {
        setLayout(new BorderLayout());
        //创建North:左=连接区,中=我的信息编辑区,右上角=主题小按钮
        JPanel connPanel = new JPanel(new GridLayout(0,2,2,2));
        connPanel.add(new JLabel("Host address:"));
        connPanel.add(txtHost = new JTextField(ChatClient.serverText));
        connPanel.add(new JLabel("Port:"));
        connPanel.add(txtPort = new JTextField(ChatClient.portText));
        connPanel.add(new JLabel("Nick:"));
        connPanel.add(txtNick = new JTextField(ChatClient.nickText));
        connPanel.add(buttonScan = new JButton("扫描局域网"));
        connPanel.add(buttonConnect = new JButton("Connect"));
        connPanel.setPreferredSize(new Dimension(300, 0));
        // "我的信息":个人信息直接在主界面查看/修改(功能五的 UI 化)
        myInfoRows = new JPanel(new GridLayout(0,2,2,2));
        JPanel myInfoPanel = new JPanel(new BorderLayout());
        JPanel infoHead = new JPanel(new BorderLayout());
        infoHead.add(new JLabel("  我的信息(左=字段 右=内容;清空内容保存=删除该字段)"), BorderLayout.CENTER);
        buttonTheme = new JButton(Theme.isDark() ? "日" : "夜"); // 主题切换缩成右上角小按钮
        buttonTheme.setMargin(new Insets(0,6,0,6));
        buttonTheme.setToolTipText("切换日间/夜间模式");
        infoHead.add(buttonTheme, BorderLayout.EAST);
        myInfoPanel.add(infoHead, BorderLayout.NORTH);
        myInfoPanel.add(new JScrollPane(myInfoRows), BorderLayout.CENTER);
        JPanel infoBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 1));
        infoBtns.add(buttonAddRow = new JButton("再加一行"));
        infoBtns.add(buttonSaveInfo = new JButton("保存信息"));
        myInfoPanel.add(infoBtns, BorderLayout.SOUTH);
        addInfoRow("", "");
        northPanel = new JPanel(new BorderLayout(6, 0));
        northPanel.add(connPanel, BorderLayout.WEST);
        northPanel.add(myInfoPanel, BorderLayout.CENTER);
        northPanel.setPreferredSize(new Dimension(0, 122));
        buttonConnect.addActionListener(this);
        buttonScan.addActionListener(this);
        buttonTheme.addActionListener(this);
        buttonAddRow.addActionListener(this);
        buttonSaveInfo.addActionListener(this);
        txtHost.addKeyListener(this);
        txtHost.addFocusListener(this);
        txtNick.addFocusListener(this);
        txtNick.addKeyListener(this);
        txtPort.addKeyListener(this);
        txtPort.addFocusListener(this);
        buttonConnect.addKeyListener(this);
        this.add(northPanel, BorderLayout.NORTH);
        //创建Sourth
        southPanel = new JPanel();
        southPanel.add(msgWindow = new JTextField(20));
        southPanel.add(buttonSend = new JButton("Send"));
        southPanel.add(buttonFile = new JButton("文件"));
        buttonSend.addActionListener(this);
        buttonFile.addActionListener(this);
        msgWindow.addKeyListener(this);
        add(southPanel, BorderLayout.SOUTH);
        //创建Center：CardLayout，每个会话一个独立聊天窗格
        centerLayout = new CardLayout();
        centerCards = new JPanel(centerLayout);
        historyWindow = new ClientHistory(); // 公共聊天室窗格
        convs.put(MAIN_ROOM, historyWindow);
        sc = new JScrollPane(historyWindow);
        sc.setAutoscrolls(true);
        centerCards.add(sc, MAIN_ROOM);
        this.add(centerCards, BorderLayout.CENTER);
        //创建East：在线用户列表（服务器推送，实时刷新）
        userModel = new DefaultListModel();
        userList = new JList(userModel);
        // 双击在线用户名 → 输入框自动填好私聊命令前缀，光标就位直接打内容
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)) return; // 右键交给弹出菜单
                // 点列表空白处取消选中（选中现在只是视觉焦点，不再影响文件发送目标）
                int idx = userList.locationToIndex(e.getPoint());
                if(idx >= 0 && !userList.getCellBounds(idx, idx).contains(e.getPoint())) {
                    userList.clearSelection();
                    return;
                }
                if(e.getClickCount() == 2) {
                    Object v = userList.getSelectedValue();
                    if(v != null) {
                        if(v.toString().equalsIgnoreCase(txtNick.getText())) return; // 不能和自己开会话
                        openConv(v.toString()); // 双击在线用户 → 打开/切换到与他的私聊会话
                    }
                }
            }
            // popup trigger 在 Windows 是 released、在部分平台是 pressed，两处都挂
            public void mousePressed(MouseEvent e) { userMenu(e); }
            public void mouseReleased(MouseEvent e) { userMenu(e); }
        });
        JPanel eastPanel = new JPanel(new BorderLayout());
        eastPanel.add(new JLabel("在线用户", JLabel.CENTER), BorderLayout.NORTH);
        eastPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        //East 用 CardLayout：公共聊天室显示在线用户，私聊会话显示对方信息
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(new JLabel("对方信息", JLabel.CENTER), BorderLayout.NORTH);
        infoPane = new JEditorPane("text/html", "");
        infoPane.setEditable(false);
        infoPanel.add(new JScrollPane(infoPane), BorderLayout.CENTER);
        infoPanel.add(buttonAddFriend = new JButton("加为好友"), BorderLayout.SOUTH);
        buttonAddFriend.addActionListener(this);
        eastLayout = new CardLayout();
        eastCards = new JPanel(eastLayout);
        eastCards.add(eastPanel, "users");
        eastCards.add(infoPanel, "info");
        eastCards.setPreferredSize(new Dimension(140, 0));
        this.add(eastCards, BorderLayout.EAST);
        //创建West：会话列表（公共聊天室 / 好友 / 临时会话）
        convModel = new DefaultListModel();
        convList = new JList(convModel);
        convList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)) return; // 右键交给弹出菜单
                int idx = convList.locationToIndex(e.getPoint());
                if(idx < 0 || idx >= itemNames.size()) return;
                Object name = itemNames.get(idx);
                if(name != null) openConv(name.toString()); // 表头行(null)不响应点击
            }
            public void mousePressed(MouseEvent e) { convMenu(e); }
            public void mouseReleased(MouseEvent e) { convMenu(e); }
        });
        JScrollPane convScroll = new JScrollPane(convList);
        convScroll.setPreferredSize(new Dimension(150, 0));
        this.add(convScroll, BorderLayout.WEST);
        rebuildConvList();
        applyTheme(); // 启动即按用户上次选择的主题渲染
    }
    // ===== 主题(功能十四):遍历组件树刷色 + 各会话窗格整体重渲染 =====
    private void applyTheme() {
        themeWalk(getContentPane());
        Iterator it = convs.values().iterator();
        while(it.hasNext()) ((ClientHistory)(it.next())).renderAll();
        infoPane.setText(Theme.apply(rawInfoHtml));
        infoPane.setBackground(Theme.bgColor());
        buttonTheme.setText(Theme.isDark() ? "日" : "夜");
        repaint();
    }
    private void themeWalk(Component c) {
        if(c instanceof JPanel || c instanceof JScrollPane || c instanceof JViewport)
            c.setBackground(Theme.panelColor());
        if(c instanceof JList || c instanceof JTextField || c instanceof JEditorPane) {
            c.setBackground(Theme.bgColor());
            c.setForeground(Theme.fgColor());
        }
        if(c instanceof JLabel) c.setForeground(Theme.fgColor());
        if(c instanceof Container) {
            Component[] cs = ((Container)c).getComponents();
            for(int i=0;i<cs.length;i++) themeWalk(cs[i]);
        }
    }
    // ===== 局域网扫描(功能十):UDP 广播探测,服务器应答即自动填入 Host/Port =====
    private void scanLan() {
        addMsg("正在扫描局域网内的聊天服务器...");
        new Thread() { public void run() {
            try {
                java.net.DatagramSocket ds = new java.net.DatagramSocket();
                ds.setBroadcast(true);
                ds.setSoTimeout(2500);
                byte[] q = "CHAT_DISCOVER".getBytes("UTF-8");
                ds.send(new java.net.DatagramPacket(q, q.length,
                        java.net.InetAddress.getByName("255.255.255.255"), 3600));
                byte[] buf = new byte[128];
                java.net.DatagramPacket rp = new java.net.DatagramPacket(buf, buf.length);
                ds.receive(rp); // 等第一个应答
                String resp = new String(rp.getData(), 0, rp.getLength(), "UTF-8").trim();
                ds.close();
                if(resp.startsWith("CHAT_SERVER ")) {
                    String ip = rp.getAddress().getHostAddress();
                    String port = resp.substring(12).trim();
                    txtHost.setText(ip);
                    txtPort.setText(port);
                    addMsg("<font color=\"" + Theme.OK + "\">发现服务器 " + ip + ":" + port
                            + ",已自动填入,点 Connect 连接</font>");
                }
            } catch(java.net.SocketTimeoutException te) {
                addMsg("<font color=\"" + Theme.ERR + "\">未发现服务器(请确认服务器已启动且在同一局域网)</font>");
            } catch(Exception ex) {
                addMsg("<font color=\"" + Theme.ERR + "\">扫描失败: " + ex.getMessage() + "</font>");
            }
        }}.start();
    }
   public static void main(String args[]) {
        ChatClient client = new ChatClient();
        client.setTitle(client.appName);
        client.setSize(800, 540);
        client.setLocation(100,100);
        client.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.setVisible(true);
        client.msgWindow.requestFocus();
    }
    public void addMsg(String str) {
        // 服务器推送（0x01 开头）走单独处理，不进聊天区。
        // 聊天广播都带 "昵称:" 前缀，首字符不可能是控制符，无法伪造
        if(str.length() > 0 && str.charAt(0) == ClientKernel.PUSHMARKER) {
            handlePush(str.substring(1));
            return;
        }
        appendTo(MAIN_ROOM, str); // 普通广播与系统提示都属于公共聊天室
        syncNick(str);
        // 自动重连取回注册昵称:服务器要求验证且缓存过密码 → 自动 /verify(功能十一)
        if(str.indexOf("is registered so you have to verify") >= 0
            && lastPassword != null && ck != null && ck.isConnected()) {
            ck.sendMessage("/verify " + lastPassword);
            appendTo(MAIN_ROOM, "<font color=\"" + Theme.SELF + "\">已用缓存的密码自动验证身份...</font>");
        }
    }
    // 处理服务器主动推送：USERLIST 在线名单 / FRIENDS 好友名单 / PM,PMSENT 私聊 /
    // FILE 群发文件 / FILEP 私发文件 / USERINFO 个人信息
    private void handlePush(String cmd) {
        if(cmd.startsWith("USERLIST")) {
            userModel.clear();
            online.clear();
            StringTokenizer st = new StringTokenizer(cmd.substring(8));
            while(st.hasMoreTokens()) {
                String u = st.nextToken();
                userModel.addElement(u);
                online.add(u);
            }
            rebuildConvList(); // 好友的在线/离线标记随之刷新
        } else if(cmd.startsWith("FRIENDS")) {
            friends.clear();
            if(cmd.length() > 7) {
                StringTokenizer st = new StringTokenizer(cmd.substring(7));
                while(st.hasMoreTokens()) friends.add(st.nextToken());
            }
            // 已有会话的人被移出好友后归入临时会话，避免从列表上消失
            Iterator it = convs.keySet().iterator();
            while(it.hasNext()) {
                String k = (String)(it.next());
                if(!MAIN_ROOM.equals(k) && !containsIgnoreCase(friends, k)
                    && !containsIgnoreCase(tempConvs, k)) tempConvs.add(k);
            }
            // 好友关系变化可能影响右栏"加为好友"按钮的显隐
            if(!MAIN_ROOM.equals(currentConv))
                buttonAddFriend.setVisible(!containsIgnoreCase(friends, currentConv));
            rebuildConvList();
        } else if(cmd.startsWith("MSG ")) {
            // 带唯一 ID 的群聊消息: MSG <id> <昵称> <正文>。ID 作行键存储,为撤回/回执(阶段二)留口
            String[] t = split3(cmd.substring(4));
            if(t == null) return;
            ensureConv(MAIN_ROOM).addOrUpdate("m" + t[0], "<b>" + t[1] + "</b>: " + t[2]);
            markUnread(MAIN_ROOM);
            syncNick(t[1] + ":" + t[2]);
        } else if(cmd.startsWith("PM ")) {
            // 私聊: PM <id> <发送者> <正文> → 路由进与发送者的会话窗格
            String[] t = split3(cmd.substring(3));
            if(t == null) return;
            ensureConv(t[1]).addOrUpdate("m" + t[0], "<b>" + t[1] + "</b>: " + t[2]);
            markUnread(t[1]);
        } else if(cmd.startsWith("PMSENT ")) {
            // 我发出的私聊回显: PMSENT <id> <目标> <正文> → 同一会话,灰色区分
            String[] t = split3(cmd.substring(7));
            if(t == null) return;
            ensureConv(t[1]).addOrUpdate("m" + t[0],
                "<font color=\"" + Theme.SELF + "\">我: " + t[2] + "</font>");
        } else if(cmd.startsWith("LOST")) {
            // 断线(功能十一):红字提示 + 自动重连
            appendTo(MAIN_ROOM, "<font color=\"" + Theme.ERR + "\">与服务器的连接已断开,自动重连中...</font>");
            startAutoReconnect();
        } else if(cmd.startsWith("FSTART ")) {
            fileStart(cmd.substring(7));
        } else if(cmd.startsWith("FCHUNK ")) {
            fileChunk(cmd.substring(7));
        } else if(cmd.startsWith("FEND ")) {
            fileEnd(cmd.substring(5).trim());
        } else if(cmd.startsWith("FILEP ")) {
            receiveFile(cmd.substring(6), null); // 旧版单包文件(兼容保留)
        } else if(cmd.startsWith("FILE ")) {
            receiveFile(cmd.substring(5), MAIN_ROOM);
        } else if(cmd.startsWith("USERINFO ")) {
            showUserInfo(cmd.substring(9));
        }
    }
    // 把 "a b 其余部分" 切成三段(前两段无空格,第三段可含任意内容)
    private String[] split3(String s) {
        int a = s.indexOf(' ');
        int b = (a < 0) ? -1 : s.indexOf(' ', a + 1);
        if(b < 0) return null;
        return new String[]{ s.substring(0, a), s.substring(a + 1, b), s.substring(b + 1) };
    }
    private void markUnread(String conv) {
        if(!conv.equals(currentConv)) { unread.add(conv); rebuildConvList(); }
    }
    // 接收方文件展示 HTML:图片缩略图内嵌/视频点击播放/文件点击打开(收发两侧规则一致)
    private String buildRecvHtml(String sender, java.io.File out, String fname) {
        String lower = fname.toLowerCase();
        String uri = "" + out.toURI();
        if(lower.endsWith(".png") || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
            return "<font color=\"" + Theme.PRIV + "\">[图片] 来自 " + sender + ": " + fname
                    + "</font>（<a href=\"" + uri + "\">查看原图</a>）<br><img src=\"" + uri + "\"" + imgSizeAttr(out) + ">";
        } else if(lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")
            || lower.endsWith(".mov") || lower.endsWith(".wmv")) {
            return "<font color=\"" + Theme.PRIV + "\">[视频] 来自 " + sender + ": " + fname
                    + "（已保存，<a href=\"" + uri + "\">点击播放</a>）</font>";
        }
        return "<font color=\"" + Theme.PRIV + "\">[文件] 来自 " + sender + ": " + fname
                + "（已保存到 " + out.getAbsolutePath() + "，<a href=\"" + uri + "\">打开</a>）</font>";
    }
    // 旧版单包文件接收(兼容保留):解码 Base64 存盘后展示
    private void receiveFile(String rest, String route) {
        try {
            int p1 = rest.indexOf(' ');
            int p2 = rest.indexOf(' ', p1 + 1);
            if(p1 < 0 || p2 < 0) return;
            String sender = rest.substring(0, p1);
            String pane = (route == null) ? sender : route;
            // 文件名来自网络，剥掉路径分隔符防止目录穿越（如 ..\evil.exe 写到别处）
            String fname = rest.substring(p1 + 1, p2).replace('\\', '_').replace('/', '_');
            byte[] data = Base64.getDecoder().decode(rest.substring(p2 + 1));
            java.io.File out = downloadTarget(fname);
            java.nio.file.Files.write(out.toPath(), data);
            appendTo(pane, buildRecvHtml(sender, out, fname));
        } catch(Exception ex) {
            appendTo(MAIN_ROOM, "<font color=\"" + Theme.ERR + "\">接收文件失败: " + ex.getMessage() + "</font>");
        }
    }
    private java.io.File downloadTarget(String fname) {
        java.io.File dir = new java.io.File(System.getProperty("user.home"), "ChatDownloads");
        dir.mkdirs();
        java.io.File out = new java.io.File(dir, fname);
        if(out.exists()) out = new java.io.File(dir, System.currentTimeMillis() + "_" + fname);
        return out;
    }
    // ===== 分块文件接收(功能十二):FSTART 建档开流 → FCHUNK 顺序落盘+进度 → FEND 定稿展示 =====
    static class FileRecv {
        String sender, fname, pane;
        long size;
        int count, got, lastPct = -1;
        java.io.File out;
        java.io.FileOutputStream os;
    }
    private void fileStart(String rest) {
        try { // FSTART <发送者> <P|B> <tid> <文件名> <大小> <块数>
            StringTokenizer st = new StringTokenizer(rest);
            FileRecv r = new FileRecv();
            r.sender = st.nextToken();
            String scope = st.nextToken();
            String tid = st.nextToken();
            r.fname = st.nextToken().replace('\\', '_').replace('/', '_'); // 防目录穿越
            r.size = Long.parseLong(st.nextToken());
            r.count = Integer.parseInt(st.nextToken());
            r.pane = "P".equals(scope) ? r.sender : MAIN_ROOM;
            r.out = downloadTarget(r.fname);
            r.os = new java.io.FileOutputStream(r.out);
            recvs.put(tid, r);
            ensureConv(r.pane).addOrUpdate(tid, "<font color=\"" + Theme.PRIV + "\">[文件] 来自 "
                    + r.sender + ": " + r.fname + " 接收中 0%</font>");
        } catch(Exception ex) {
            appendTo(MAIN_ROOM, "<font color=\"" + Theme.ERR + "\">接收文件失败: " + ex.getMessage() + "</font>");
        }
    }
    private void fileChunk(String rest) {
        try { // FCHUNK <tid> <序号> <Base64块>
            int a = rest.indexOf(' ');
            int b = rest.indexOf(' ', a + 1);
            if(b < 0) return;
            String tid = rest.substring(0, a);
            FileRecv r = (FileRecv)recvs.get(tid);
            if(r == null) return;
            r.os.write(Base64.getDecoder().decode(rest.substring(b + 1))); // TCP+FIFO 队列保证块按序到达
            r.got++;
            int pct = (int)(r.got * 100L / r.count);
            if(pct != r.lastPct) { // 进度只在百分比变化时重绘,避免高频重渲染
                r.lastPct = pct;
                ensureConv(r.pane).addOrUpdate(tid, "<font color=\"" + Theme.PRIV + "\">[文件] 来自 "
                        + r.sender + ": " + r.fname + " 接收中 " + pct + "%</font>");
            }
        } catch(Exception ex) {}
    }
    private void fileEnd(String tid) {
        try {
            FileRecv r = (FileRecv)recvs.remove(tid);
            if(r == null) return;
            r.os.close();
            String warn = (r.out.length() != r.size)
                ? "<font color=\"" + Theme.ERR + "\">(大小不符,可能不完整)</font>" : "";
            ensureConv(r.pane).addOrUpdate(tid, buildRecvHtml(r.sender, r.out, r.fname) + warn);
            markUnread(r.pane);
        } catch(Exception ex) {}
    }
    // ===== 会话管理 =====
    private ClientHistory ensureConv(String name) {
        ClientHistory h = (ClientHistory)convs.get(name);
        if(h == null) {
            h = new ClientHistory();
            convs.put(name, h);
            centerCards.add(new JScrollPane(h), name);
            if(!containsIgnoreCase(friends, name)) tempConvs.add(name); // 非好友归入临时会话分组
            rebuildConvList();
        }
        return h;
    }
    private void appendTo(String conv, String html) {
        ensureConv(conv).addText(html);
        if(!conv.equals(currentConv)) { unread.add(conv); rebuildConvList(); } // 非当前会话标未读
    }
    void openConv(String name) {
        currentConv = name;
        ensureConv(name);
        unread.remove(name);
        centerLayout.show(centerCards, name);
        if(MAIN_ROOM.equals(name)) {
            eastLayout.show(eastCards, "users");
        } else {
            eastLayout.show(eastCards, "info");
            buttonAddFriend.setVisible(!containsIgnoreCase(friends, name)); // 已是好友就不显示"加为好友"
            infoPane.setText("查询中...");
            if(ck != null && ck.isConnected()) ck.sendMessage("/infoq " + name); // 静默拉取对方信息填右栏
        }
        rebuildConvList();
        msgWindow.requestFocus();
    }
    // 重建左侧会话列表：公共聊天室 / 好友(带在线标记) / 临时会话；未读会话加 ● 前缀
    private void rebuildConvList() {
        if(convModel == null) return;
        convModel.clear();
        itemNames.clear();
        addConvItem(MAIN_ROOM, MAIN_ROOM);
        addConvItem("── 好友 ──", null);
        for(int i=0;i<friends.size();i++) {
            String f = (String)friends.get(i);
            addConvItem(f + (containsIgnoreCase(online, f) ? " [在线]" : " [离线]"), f);
        }
        boolean headerAdded = false;
        for(int i=0;i<tempConvs.size();i++) {
            String t = (String)tempConvs.get(i);
            if(containsIgnoreCase(friends, t)) continue; // 已加为好友的会话不再算"临时"
            if(!headerAdded) { addConvItem("── 临时会话 ──", null); headerAdded = true; }
            addConvItem(t, t);
        }
        int cur = itemNames.indexOf(currentConv);
        if(cur >= 0) convList.setSelectedIndex(cur);
    }
    private void addConvItem(String display, String name) {
        convModel.addElement((name != null && unread.contains(name) ? "● " : "") + display);
        itemNames.add(name);
    }
    private boolean containsIgnoreCase(Vector v, String s) {
        for(int i=0;i<v.size();i++)
            if(s.equalsIgnoreCase((String)v.get(i))) return true;
        return false;
    }
    // 在线用户列表右键菜单：添加好友（已是好友则只有"发起私聊"；自己不弹）
    private void userMenu(MouseEvent e) {
        if(!e.isPopupTrigger()) return;
        int idx = userList.locationToIndex(e.getPoint());
        if(idx < 0 || !userList.getCellBounds(idx, idx).contains(e.getPoint())) return;
        userList.setSelectedIndex(idx);
        final String name = "" + userModel.get(idx);
        if(name.equalsIgnoreCase(txtNick.getText())) return;
        JPopupMenu menu = new JPopupMenu();
        if(!containsIgnoreCase(friends, name)) {
            JMenuItem add = new JMenuItem("添加好友");
            add.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    if(ck != null && ck.isConnected()) ck.sendMessage("/addfriend " + name);
                }
            });
            menu.add(add);
        }
        JMenuItem chat = new JMenuItem("发起私聊");
        chat.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) { openConv(name); }
        });
        menu.add(chat);
        menu.show(userList, e.getX(), e.getY());
    }
    // 会话列表右键菜单：仅好友条目提供"删除好友"
    private void convMenu(MouseEvent e) {
        if(!e.isPopupTrigger()) return;
        int idx = convList.locationToIndex(e.getPoint());
        if(idx < 0 || idx >= itemNames.size()) return;
        Object o = itemNames.get(idx);
        if(o == null) return;
        final String name = o.toString();
        if(!containsIgnoreCase(friends, name)) return;
        convList.setSelectedIndex(idx);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem del = new JMenuItem("删除好友");
        del.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                if(ck != null && ck.isConnected()) ck.sendMessage("/delfriend " + name);
            }
        });
        menu.add(del);
        menu.show(convList, e.getX(), e.getY());
    }
    // 计算 <img> 的等比缩略尺寸属性（240px 上限）；发送与接收两侧共用
    private String imgSizeAttr(java.io.File imgFile) {
        try {
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(imgFile);
            if(bi != null) {
                double sc = Math.min(1.0, 240.0 / Math.max(bi.getWidth(), bi.getHeight()));
                return " width=\"" + (int)(bi.getWidth()*sc) + "\" height=\"" + (int)(bi.getHeight()*sc) + "\"";
            }
        } catch(Exception ig) {}
        return "";
    }
    // 自己发出的图片/视频/文件在本端同样可查看：直接引用本地原文件展示（无需等对方转发）
    String buildSentHtml(String target, java.io.File f, String fname) {
        String uri = "" + f.toURI();
        String head = "<font color=\"" + Theme.SELF + "\">我" + ("*".equals(target) ? "(群发)" : "") + ": </font>";
        String lower = fname.toLowerCase();
        if(lower.endsWith(".png") || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
            return head + "<font color=\"" + Theme.PRIV + "\">[图片] " + fname
                    + "</font>（<a href=\"" + uri + "\">查看原图</a>）<br><img src=\"" + uri + "\"" + imgSizeAttr(f) + ">";
        } else if(lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")
            || lower.endsWith(".mov") || lower.endsWith(".wmv")) {
            return head + "<font color=\"" + Theme.PRIV + "\">[视频] " + fname
                    + "（<a href=\"" + uri + "\">点击播放</a>）</font>";
        }
        return head + "<font color=\"" + Theme.PRIV + "\">[文件] " + fname
                + "（<a href=\"" + uri + "\">打开</a>）</font>";
    }
    // ===== 分块发送(功能十二):切块编号,块间隙让聊天消息插队(交织发送),双侧实时进度 =====
    void sendFileChunked(final String target, final java.io.File f) {
        final String fname = f.getName().replace(' ', '_');
        final String tid = "t" + System.currentTimeMillis() + "_" + (++tidCounter);
        final String pane = "*".equals(target) ? MAIN_ROOM : target;
        new Thread() { public void run() {
            try {
                int CHUNK = 64 * 1024; // 64KB/块:Base64 后约 87KB,兼顾单条消息大小与总块数
                long size = f.length();
                int count = (int)((size + CHUNK - 1) / CHUNK);
                if(count == 0) count = 1;
                ck.sendMessage("/fstart " + target + " " + tid + " " + fname + " " + size + " " + count);
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                byte[] buf = new byte[CHUNK];
                int lastPct = -1;
                for(int i = 0; i < count; i++) {
                    int n = in.read(buf);
                    if(n < 0) n = 0;
                    byte[] part = new byte[n];
                    System.arraycopy(buf, 0, part, 0, n);
                    // 交织发送:发送队列有积压就等一等,让期间输入的聊天消息插进块间隙
                    while(ck.pendingCount() > 1) { try { Thread.sleep(15); } catch(Exception e) {} }
                    ck.sendMessage("/fchunk " + tid + " " + i + " " + Base64.getEncoder().encodeToString(part));
                    int pct = (int)((i + 1) * 100L / count);
                    if(pct != lastPct) { // 进度只在百分比变化时重绘
                        lastPct = pct;
                        ensureConv(pane).addOrUpdate(tid, "<font color=\"" + Theme.SELF + "\">我"
                                + ("*".equals(target) ? "(群发)" : "") + ": </font><font color=\"" + Theme.PRIV
                                + "\">[文件] " + fname + " 发送中 " + pct + "%</font>");
                    }
                }
                in.close();
                ck.sendMessage("/fend " + tid);
                ensureConv(pane).addOrUpdate(tid, buildSentHtml(target, f, fname));
            } catch(Exception ex) {
                appendTo(MAIN_ROOM, "<font color=\"" + Theme.ERR + "\">发送文件失败: " + ex.getMessage() + "</font>");
            }
        }}.start();
    }
    // 渲染 USERINFO 推送到右侧信息栏，格式："用户 字段: 值|字段: 值"
    private void showUserInfo(String rest) {
        int p = rest.indexOf(' ');
        String user = (p < 0) ? rest : rest.substring(0, p);
        if(user.equalsIgnoreCase(txtNick.getText().trim())) { // 自己的信息 → 顶部"我的信息"编辑区
            populateMyInfo(p < 0 ? "" : rest.substring(p + 1));
            return;
        }
        if(MAIN_ROOM.equals(currentConv) || !user.equalsIgnoreCase(currentConv)) return; // 只更新当前会话对象
        String html = "<b>" + user + "</b><br>";
        html += containsIgnoreCase(online, user) ? "<font color=\"" + Theme.OK + "\">[在线]</font><br>"
                                                 : "<font color=\"" + Theme.SELF + "\">[离线]</font><br>";
        if(p < 0) html += "<br>暂无个人信息";
        else {
            StringTokenizer st = new StringTokenizer(rest.substring(p + 1), "|");
            while(st.hasMoreTokens()) html += "<br>" + st.nextToken();
        }
        rawInfoHtml = html; // 存原始(占位符)版本,切主题时可重渲染
        infoPane.setText(Theme.apply(html));
    }
    // 收到服务器改名成功回执时，把最新昵称同步到顶部 Nick 框。
    // 只认以 "Server:" 开头的消息：聊天广播都带 "昵称:" 前缀，不会被别人发的内容伪造
    private void syncNick(String str) {
        String marker = "ou are now known as "; // 兼容 "You are..." 和 "...you are..." 两种回执
        int i = str.indexOf(marker);
        if(str.startsWith("Server:") && i > 0) {
            txtNick.setText(str.substring(i + marker.length()).trim());
            // 昵称确立即拉取自己的信息回填顶部编辑区
            if(ck != null && ck.isConnected()) ck.sendMessage("/infoq " + txtNick.getText().trim());
        }
    }
    // ===== "我的信息"编辑区(个人信息功能的界面化) =====
    void addInfoRow(String field, String value) {
        JTextField f = new JTextField(field);
        JTextField v = new JTextField(value);
        f.setBackground(Theme.bgColor()); f.setForeground(Theme.fgColor());
        v.setBackground(Theme.bgColor()); v.setForeground(Theme.fgColor());
        myInfoRows.add(f);
        myInfoRows.add(v);
        infoFieldRows.add(new JTextField[]{ f, v });
        myInfoRows.revalidate();
        myInfoRows.repaint();
    }
    // 保存:逐行下发——有字段有内容 /setinfo,有字段没内容 /delinfo;完成后重新拉取回填
    private void saveMyInfo() {
        if(ck == null || !ck.isConnected()) {
            addMsg("<font color=\"" + Theme.ERR + "\">尚未连接服务器，请先点击 Connect</font>");
            return;
        }
        for(int i=0;i<infoFieldRows.size();i++) {
            JTextField[] row = (JTextField[])infoFieldRows.get(i);
            String f = row[0].getText().trim().replace(' ', '_'); // 字段名在协议中是单个词
            String v = row[1].getText().trim();
            if(f.length() == 0) continue;
            if(v.length() == 0) ck.sendMessage("/delinfo " + f);
            else ck.sendMessage("/setinfo " + f + " " + v);
        }
        String n = txtNick.getText().trim();
        if(n.length() > 0 && !n.equals(ChatClient.nickText))
            ck.sendMessage("/infoq " + n); // FIFO 保证在上面的写操作之后处理,拿到的是最新状态
    }
    // 服务器推回自己的 USERINFO("字段: 值|字段: 值")→ 回填编辑区,末尾始终留一行空行
    void populateMyInfo(String data) {
        myInfoRows.removeAll();
        infoFieldRows.clear();
        if(data != null && data.trim().length() > 0) {
            StringTokenizer st = new StringTokenizer(data, "|");
            while(st.hasMoreTokens()) {
                String t = st.nextToken();
                int p = t.indexOf(": ");
                if(p > 0) addInfoRow(t.substring(0, p).trim(), t.substring(p + 2));
            }
        }
        addInfoRow("", "");
    }
    private void connect() {
        try {
            reconnecting = false; // 手动连接取消进行中的自动重连
            if(ck!=null) ck.dropMe();
            ck = new ClientKernel(txtHost.getText(), Integer.parseInt(txtPort.getText()));
            // 昵称框还是占位符 "YourName" 时不自动改名，否则多个客户端会互相抢注同一个名字
            boolean nickSet = !txtNick.getText().equals(ChatClient.nickText)
                              && txtNick.getText().trim().length() > 0;
            if(nickSet) ck.setNick(txtNick.getText());
            if(ck.isConnected()) {
                ck.addClient(this);
                addMsg("<font color=\"" + Theme.OK + "\">connected! Local Port:" + ck.getLocalPort() + "</font>");
                if(!nickSet) addMsg("提示：昵称框未填写，当前昵称为端口号，可在上方 Nick 框输入名字后回车，或发送 /nick 名字 修改");
            } else {
                addMsg("<font color=\"" + Theme.ERR + "\">connect failed！</font>");
            }
        } catch(Exception e) { e.printStackTrace(); }
    }
    // ===== 自动重连(功能十一):断线后每 3 秒尝试一次,成功后取回昵称并补发待发消息 =====
    private void startAutoReconnect() {
        if(reconnecting) return;
        reconnecting = true;
        new Thread() { public void run() {
            for(int attempt = 1; reconnecting && attempt <= 30; attempt++) {
                try { Thread.sleep(3000); } catch(Exception e) {}
                if(!reconnecting) return; // 用户手动 Connect 了
                appendTo(MAIN_ROOM, "<font color=\"" + Theme.SELF + "\">自动重连中(第 " + attempt + " 次)...</font>");
                try {
                    ClientKernel nk = new ClientKernel(txtHost.getText(), Integer.parseInt(txtPort.getText()));
                    if(nk.isConnected()) {
                        if(ck != null) ck.dropMe();
                        ck = nk;
                        ck.addClient(ChatClient.this);
                        appendTo(MAIN_ROOM, "<font color=\"" + Theme.OK + "\">已自动重连! Local Port:"
                                + ck.getLocalPort() + "</font>");
                        String n = txtNick.getText().trim();
                        if(n.length() > 0 && !n.equals(ChatClient.nickText)) ck.setNick(n); // 取回原昵称
                        try { Thread.sleep(400); } catch(Exception e) {}
                        flushPending();
                        reconnecting = false;
                        return;
                    }
                    nk.dropMe();
                } catch(Exception ex) {}
            }
            reconnecting = false;
            appendTo(MAIN_ROOM, "<font color=\"" + Theme.ERR + "\">自动重连失败,请手动 Connect</font>");
        }}.start();
    }
    private void flushPending() {
        while(pendingOut.size() > 0) {
            ck.sendMessage((String)pendingOut.remove(0)); // 断线期间的消息按序补发
        }
    }
    private void send() {
        String toSend = msgWindow.getText();
        if(toSend == null || toSend.trim().length() == 0) return;
        // 缓存注册/验证密码,断线自动重连后凭它自动取回注册昵称
        if(toSend.startsWith("/register ") || toSend.startsWith("/verify ")) {
            StringTokenizer st = new StringTokenizer(toSend);
            String last = null;
            while(st.hasMoreTokens()) last = st.nextToken();
            if(last != null && !last.startsWith("/")) lastPassword = last;
        }
        // 会话感知：私聊会话里输入的普通文字自动转为 /msg 对方 内容；命令(/开头)原样发
        String wire = (!MAIN_ROOM.equals(currentConv) && !toSend.startsWith("/"))
                    ? "/msg " + currentConv + " " + toSend : toSend;
        if(ck == null || !ck.isConnected()) {
            if(reconnecting) { // 断线重连中:进待发队列,恢复后补发
                pendingOut.add(wire);
                appendTo(currentConv, "<font color=\"" + Theme.SELF + "\">[断线待发] " + toSend + "</font>");
                lastMsg = "" + toSend;
                msgWindow.setText("");
                return;
            }
            addMsg("<font color=\"" + Theme.ERR + "\">尚未连接服务器，请先点击 Connect</font>");
            return;
        }
        ck.sendMessage(wire);
        lastMsg = "" + toSend;
        msgWindow.setText("");
    }
    public void keyPressed(KeyEvent e) {
    }
    public void keyReleased(KeyEvent e) {
        if(e.getSource() == msgWindow && e.getKeyCode() == KeyEvent.VK_UP) msgWindow.setText(lastMsg);
    }
    public void keyTyped(KeyEvent e) {
        if(e.getKeyChar() ==KeyEvent.VK_ENTER) {
            if(e.getSource() == msgWindow) send();
            if(e.getSource() == txtNick) {
                // 已连接时在 Nick 框回车 = 直接改名（原逻辑会整个断线重连）；未连接时才发起连接
                String n = txtNick.getText().trim();
                if(ck != null && ck.isConnected()) {
                    if(n.length() > 0 && !n.equals(ChatClient.nickText)) ck.setNick(n);
                } else connect();
                msgWindow.requestFocus();
            }
            if(e.getSource() == txtHost) txtPort.requestFocus();
            if(e.getSource() == txtPort) txtNick.requestFocus();
        }
    }
    public void actionPerformed(ActionEvent e) {
        if(e.getSource()==buttonConnect) connect();
        if(e.getSource()==buttonSend) send();
        if(e.getSource()==buttonFile) sendFile();
        if(e.getSource()==buttonScan) scanLan();
        if(e.getSource()==buttonTheme) { Theme.toggle(); applyTheme(); }
        if(e.getSource()==buttonAddRow) addInfoRow("", "");
        if(e.getSource()==buttonSaveInfo) saveMyInfo();
        if(e.getSource()==buttonAddFriend && !MAIN_ROOM.equals(currentConv)
            && ck != null && ck.isConnected())
            ck.sendMessage("/addfriend " + currentConv); // 右栏一键加好友，回执进公共聊天室
    }
    // 发送文件/图片/视频:公共聊天室=群发,私聊会话=发给会话对方;走分块传输(功能十二)
    private void sendFile() {
        if(ck == null || !ck.isConnected()) {
            addMsg("<font color=\"" + Theme.ERR + "\">尚未连接服务器，请先点击 Connect</font>");
            return;
        }
        String target = MAIN_ROOM.equals(currentConv) ? "*" : currentConv;
        JFileChooser fc = new JFileChooser();
        if(fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        if(f.length() > 200L*1024*1024) { // 分块传输解除了 20MB 限制,保留 200MB 上限防误操作
            addMsg("<font color=\"" + Theme.ERR + "\">文件过大（限 200MB）：" + f.getName() + "</font>");
            return;
        }
        sendFileChunked(target, f);
    }
    public void focusGained(FocusEvent e) {
        if(e.getSource()==txtHost && txtHost.getText().equals(ChatClient.serverText)) txtHost.setText("");
        if(e.getSource()==txtPort && txtPort.getText().equals(ChatClient.portText)) txtPort.setText("");
        if(e.getSource()==txtNick && txtNick.getText().equals(ChatClient.nickText)) txtNick.setText("");
    }
    public void focusLost(FocusEvent e) {
       if(e.getSource()==txtPort && txtPort.getText().equals("")) txtPort.setText(ChatClient.portText);
       if(e.getSource()==txtHost && txtHost.getText().equals("")) txtHost.setText(ChatClient.serverText);
       if(e.getSource()==txtNick && txtNick.getText().equals(ChatClient.nickText)) 
                                                            txtNick.setText(ChatClient.nickText);
    }
    // 聊天窗格(功能十四改造):消息按"行"存储,颜色一律写 %TOKEN% 占位符,
    // 渲染时按当前主题替换 → 切换日夜主题即可对全部历史消息重渲染换色。
    // 行可带键(消息ID/传输ID),按键可原位更新——进度条刷新与将来撤回/回执都靠它
    class ClientHistory extends JEditorPane {
        private Vector lines = new Vector();     // 原始行(含占位符与时间戳前缀)
        private HashMap keyIdx = new HashMap();  // 行键 -> 行号
        public ClientHistory() {
            super("text/html", "");
            setEditable(false);
            setAutoscrolls(true);
            // 点击聊天区里的链接（查看原图/播放视频/打开文件）→ 交给系统默认程序
            addHyperlinkListener(new HyperlinkListener() {
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        try { Desktop.getDesktop().open(new java.io.File(e.getURL().toURI())); }
                        catch(Exception ex) { System.out.println("open link: " + ex.getMessage()); }
                    }
                }
            });
            renderAll();
        }
        private String stamp(String str) {
            String t = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
            return "<font color=\"" + Theme.TIME + "\">[" + t + "]</font> " + str;
        }
        public void addText(String str) {
            lines.add(stamp(str));
            renderAll();
        }
        // 带键追加/原位更新:同键第二次调用替换原行(文件进度、将来的撤回与回执)
        public void addOrUpdate(String key, String str) {
            Integer i = (Integer)keyIdx.get(key);
            if(i == null) {
                keyIdx.put(key, Integer.valueOf(lines.size()));
                lines.add(stamp(str));
            } else {
                lines.set(i.intValue(), stamp(str));
            }
            renderAll();
        }
        public void renderAll() {
            setBackground(Theme.bgColor());
            StringBuffer sb = new StringBuffer("<html><body style=\"color:")
                .append(Theme.fg()).append(";background-color:").append(Theme.bg()).append("\">");
            for(int i=0;i<lines.size();i++)
                sb.append("<br>").append(Theme.apply((String)lines.get(i)));
            sb.append("</body></html>");
            setText(sb.toString());
            try { setCaretPosition(getDocument().getLength()); } catch(Exception e) {}
        }
        public void clear() {
            lines.clear();
            keyIdx.clear();
            renderAll();
        }
    }
}

