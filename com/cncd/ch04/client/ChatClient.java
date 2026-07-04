package com.cncd.ch04.client;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.border.*;
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
    JTable infoTable;                  // "个人资料"编辑表:字段|内容(功能五 UI 化)
    DefaultTableModel infoModel;
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
        //创建North:左=连接区(带标题框),中=个人资料表格,右=醒目的主题切换按钮
        JPanel connPanel = new JPanel(new GridLayout(0,2,4,4));
        connPanel.setBorder(titled("连接"));
        connPanel.add(new JLabel(" 服务器:"));
        connPanel.add(txtHost = new JTextField(ChatClient.serverText));
        connPanel.add(new JLabel(" 端口:"));
        connPanel.add(txtPort = new JTextField(ChatClient.portText));
        connPanel.add(new JLabel(" 昵称:"));
        connPanel.add(txtNick = new JTextField(ChatClient.nickText));
        connPanel.add(buttonScan = new JButton("扫描局域网"));
        connPanel.add(buttonConnect = new JButton("连接"));
        connPanel.setPreferredSize(new Dimension(280, 0));
        // 个人资料:字段/内容两列的可编辑表格(功能五 UI 化)
        infoModel = new DefaultTableModel(new Object[]{"字段","内容"}, 0);
        infoTable = new JTable(infoModel);
        infoTable.setRowHeight(22);
        infoTable.getTableHeader().setReorderingAllowed(false);
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(titled("个人资料(双击单元格编辑;内容留空并保存=删除该项)"));
        infoPanel.add(new JScrollPane(infoTable), BorderLayout.CENTER);
        JPanel infoBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        infoBtns.add(buttonAddRow = new JButton("＋ 加一项"));
        infoBtns.add(buttonSaveInfo = new JButton("保存资料"));
        infoPanel.add(infoBtns, BorderLayout.SOUTH);
        infoModel.addRow(new Object[]{"",""});
        // 主题切换:醒目大按钮,竖在右侧
        buttonTheme = new JButton();
        buttonTheme.setPreferredSize(new Dimension(92, 0));
        setThemeButtonText();
        northPanel = new JPanel(new BorderLayout(6, 0));
        northPanel.add(connPanel, BorderLayout.WEST);
        northPanel.add(infoPanel, BorderLayout.CENTER);
        northPanel.add(buttonTheme, BorderLayout.EAST);
        northPanel.setPreferredSize(new Dimension(0, 128));
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
        //创建South:输入框占满,按钮靠右
        southPanel = new JPanel(new BorderLayout(4, 0));
        southPanel.setBorder(BorderFactory.createEmptyBorder(4,6,4,6));
        msgWindow = new JTextField();
        msgWindow.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        JPanel sendBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        sendBtns.add(buttonFile = new JButton("文件"));
        sendBtns.add(buttonSend = new JButton("发送"));
        southPanel.add(msgWindow, BorderLayout.CENTER);
        southPanel.add(sendBtns, BorderLayout.EAST);
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
        JPanel peerPanel = new JPanel(new BorderLayout());
        peerPanel.add(new JLabel("对方信息", JLabel.CENTER), BorderLayout.NORTH);
        infoPane = new JEditorPane("text/html", "");
        infoPane.setEditable(false);
        peerPanel.add(new JScrollPane(infoPane), BorderLayout.CENTER);
        peerPanel.add(buttonAddFriend = new JButton("加为好友"), BorderLayout.SOUTH);
        buttonAddFriend.addActionListener(this);
        eastLayout = new CardLayout();
        eastCards = new JPanel(eastLayout);
        eastCards.add(eastPanel, "users");
        eastCards.add(peerPanel, "info");
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
    // 统一风格的分组标题边框
    private Border titled(String t) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Theme.panelColor().darker()), t);
    }
    private void setThemeButtonText() {
        buttonTheme.setText("<html><center>" + (Theme.isDark() ? "☀<br>日间" : "☾<br>夜间") + "</center></html>");
        buttonTheme.setToolTipText("切换日间/夜间模式");
    }
    // ===== 主题(功能十四):遍历组件树刷色 + 各会话窗格整体重渲染 =====
    private void applyTheme() {
        themeWalk(getContentPane());
        Iterator it = convs.values().iterator();
        while(it.hasNext()) ((ClientHistory)(it.next())).renderAll();
        infoPane.setText(Theme.apply(rawInfoHtml));
        infoPane.setBackground(Theme.bgColor());
        // 主题按钮用主题强调色,显眼
        buttonTheme.setBackground(Theme.accentColor());
        buttonTheme.setForeground(Color.white);
        setThemeButtonText();
        if(infoTable != null) {
            infoTable.setBackground(Theme.bgColor());
            infoTable.setForeground(Theme.fgColor());
            infoTable.getTableHeader().setBackground(Theme.panelColor());
            infoTable.getTableHeader().setForeground(Theme.fgColor());
        }
        repaint();
    }
    private void themeWalk(Component c) {
        if(c == buttonTheme) return; // 主题按钮保持强调色,不被统一刷成面板色
        if(c instanceof JPanel || c instanceof JScrollPane || c instanceof JViewport
            || c instanceof JTableHeader)
            c.setBackground(Theme.panelColor());
        if(c instanceof JList || c instanceof JTextField || c instanceof JTable) {
            c.setBackground(Theme.bgColor());
            c.setForeground(Theme.fgColor());
        }
        if(c instanceof ClientHistory) c.setBackground(Theme.chatBgColor()); // 聊天区用浅灰衬气泡
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
            // 带唯一 ID 的群聊消息: MSG <id> <昵称> <正文>。自己发的靠右气泡,别人的靠左
            String[] t = split3(cmd.substring(4));
            if(t == null) return;
            boolean mine = t[1].equalsIgnoreCase(txtNick.getText().trim());
            ensureConv(MAIN_ROOM).putMsg("m" + t[0], mine ? ClientHistory.SELF : ClientHistory.OTHER, t[1], t[2]);
            markUnread(MAIN_ROOM);
        } else if(cmd.startsWith("PM ")) {
            // 私聊: PM <id> <发送者> <正文> → 对方的消息,靠左气泡
            String[] t = split3(cmd.substring(3));
            if(t == null) return;
            ensureConv(t[1]).putMsg("m" + t[0], ClientHistory.OTHER, t[1], t[2]);
            markUnread(t[1]);
        } else if(cmd.startsWith("PMSENT ")) {
            // 我发出的私聊回显: PMSENT <id> <目标> <正文> → 靠右气泡
            String[] t = split3(cmd.substring(7));
            if(t == null) return;
            ensureConv(t[1]).putMsg("m" + t[0], ClientHistory.SELF, txtNick.getText().trim(), t[2]);
        } else if(cmd.startsWith("LOST")) {
            // 断线(功能十一):系统提示 + 自动重连
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
    // 文件展示 HTML(收发共用;发送方与接收方的身份由气泡朝向体现,这里不再带"来自/我"前缀)
    private String buildFileHtml(java.io.File out, String fname) {
        String lower = fname.toLowerCase();
        String uri = "" + out.toURI();
        if(lower.endsWith(".png") || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
            return "<font color=\"" + Theme.PRIV + "\">[图片] " + fname
                    + "</font>（<a href=\"" + uri + "\">查看原图</a>）<br><img src=\"" + uri + "\"" + imgSizeAttr(out) + ">";
        } else if(lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")
            || lower.endsWith(".mov") || lower.endsWith(".wmv")) {
            return "<font color=\"" + Theme.PRIV + "\">[视频] " + fname
                    + "（<a href=\"" + uri + "\">点击播放</a>）</font>";
        }
        return "<font color=\"" + Theme.PRIV + "\">[文件] " + fname
                + "（<a href=\"" + uri + "\">打开</a>）</font>";
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
            ensureConv(pane).putMsg(null, ClientHistory.OTHER, sender, buildFileHtml(out, fname));
            markUnread(pane);
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
            ensureConv(r.pane).putMsg(tid, ClientHistory.OTHER, r.sender,
                    "<font color=\"" + Theme.PRIV + "\">[文件] " + r.fname + " 接收中 0%</font>");
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
                ensureConv(r.pane).putMsg(tid, ClientHistory.OTHER, r.sender,
                        "<font color=\"" + Theme.PRIV + "\">[文件] " + r.fname + " 接收中 " + pct + "%</font>");
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
            ensureConv(r.pane).putMsg(tid, ClientHistory.OTHER, r.sender, buildFileHtml(r.out, r.fname) + warn);
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
    // 系统提示(连接/断线/错误/服务器回执)→ 居中弱化的系统消息
    private void appendTo(String conv, String html) {
        ensureConv(conv).addSystem(html);
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
    // 自己发出的文件在本端展示(引用本地原文件);群发加"(群发)"小标,身份由气泡朝向体现
    String buildSentHtml(String target, java.io.File f, String fname) {
        String tag = "*".equals(target) ? "<font color=\"" + Theme.SELF + "\">(群发) </font>" : "";
        return tag + buildFileHtml(f, fname);
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
                        final int fp = pct;
                        ensureConv(pane).putMsg(tid, ClientHistory.SELF, txtNick.getText().trim(),
                                ("*".equals(target) ? "<font color=\"" + Theme.SELF + "\">(群发) </font>" : "")
                                + "<font color=\"" + Theme.PRIV + "\">[文件] " + fname + " 发送中 " + fp + "%</font>");
                    }
                }
                in.close();
                ck.sendMessage("/fend " + tid);
                ensureConv(pane).putMsg(tid, ClientHistory.SELF, txtNick.getText().trim(), buildSentHtml(target, f, fname));
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
    // ===== 个人资料表格(个人信息功能的界面化) =====
    // 保存:逐行下发——有字段有内容 /setinfo,有字段没内容 /delinfo;完成后重新拉取回填
    private void saveMyInfo() {
        if(ck == null || !ck.isConnected()) {
            addMsg("<font color=\"" + Theme.ERR + "\">尚未连接服务器，请先点击 Connect</font>");
            return;
        }
        if(infoTable.isEditing()) infoTable.getCellEditor().stopCellEditing(); // 提交正在编辑的单元格
        for(int i=0;i<infoModel.getRowCount();i++) {
            String f = ("" + val(i,0)).trim().replace(' ', '_'); // 字段名在协议中是单个词
            String v = ("" + val(i,1)).trim();
            if(f.length() == 0) continue;
            if(v.length() == 0) ck.sendMessage("/delinfo " + f);
            else ck.sendMessage("/setinfo " + f + " " + v);
        }
        String n = txtNick.getText().trim();
        if(n.length() > 0 && !n.equals(ChatClient.nickText))
            ck.sendMessage("/infoq " + n); // FIFO 保证在上面的写操作之后处理,拿到的是最新状态
    }
    private Object val(int r, int c) { Object o = infoModel.getValueAt(r, c); return o == null ? "" : o; }
    // 服务器推回自己的 USERINFO("字段: 值|字段: 值")→ 回填表格,末尾始终留一行空行供直接输入
    void populateMyInfo(String data) {
        infoModel.setRowCount(0);
        if(data != null && data.trim().length() > 0) {
            StringTokenizer st = new StringTokenizer(data, "|");
            while(st.hasMoreTokens()) {
                String t = st.nextToken();
                int p = t.indexOf(": ");
                if(p > 0) infoModel.addRow(new Object[]{ t.substring(0, p).trim(), t.substring(p + 2) });
            }
        }
        infoModel.addRow(new Object[]{"",""});
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
        if(e.getSource()==buttonAddRow) infoModel.addRow(new Object[]{"",""});
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
    // 聊天窗格(功能二十七气泡界面 + 功能十四主题):每条消息存 {类型,发送者,正文,时间},
    // 渲染为左右对齐的气泡——自己靠右(主题色气泡)、别人靠左(带昵称)、系统消息居中弱化。
    // 颜色一律用 %TOKEN% 占位符,切主题时对全部历史重渲染即换色;带键消息可原位更新(进度/撤回)。
    class ClientHistory extends JEditorPane {
        static final int SYS = 0, OTHER = 1, SELF = 2;
        class Msg { int type; String sender, body, time; }
        private Vector msgs = new Vector();
        private HashMap keyIdx = new HashMap(); // 消息键 -> 下标
        public ClientHistory() {
            super("text/html", "");
            setEditable(false);
            setAutoscrolls(true);
            addHyperlinkListener(new HyperlinkListener() { // 点链接(原图/播放/打开)→ 系统默认程序
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        try { Desktop.getDesktop().open(new java.io.File(e.getURL().toURI())); }
                        catch(Exception ex) { System.out.println("open link: " + ex.getMessage()); }
                    }
                }
            });
            renderAll();
        }
        private String now() { return new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()); }
        public void addSystem(String body) { add(null, SYS, null, body); }
        // 带键追加/原位更新:同键第二次调用替换原消息(文件进度、将来的撤回/回执)
        public void putMsg(String key, int type, String sender, String body) { add(key, type, sender, body); }
        private void add(String key, int type, String sender, String body) {
            Integer i = (key == null) ? null : (Integer)keyIdx.get(key);
            Msg m;
            if(i == null) {
                m = new Msg();
                m.time = now();
                if(key != null) keyIdx.put(key, Integer.valueOf(msgs.size()));
                msgs.add(m);
            } else {
                m = (Msg)msgs.get(i.intValue());
            }
            m.type = type; m.sender = sender; m.body = body;
            renderAll();
        }
        private String renderMsg(Msg m) {
            if(m.type == SYS) // 系统消息:居中、小字、弱化
                return "<table width=\"100%\" cellpadding=\"2\"><tr><td align=\"center\">"
                     + "<font color=\"" + Theme.TIME + "\" size=\"2\">" + m.body + "</font></td></tr></table>";
            boolean self = m.type == SELF;
            String align = self ? "right" : "left";
            String bub = self ? Theme.SELFBUB : Theme.OTHERBUB;
            String head = self ? "<font color=\"" + Theme.TIME + "\" size=\"2\">" + m.time + "</font>"
                               : "<font color=\"" + Theme.TIME + "\" size=\"2\">" + m.sender + "&nbsp;&nbsp;" + m.time + "</font>";
            // 外层表定对齐,内层表着色即为"气泡"(Swing HTML 无圆角,以底色+留白模拟)
            return "<table width=\"100%\" cellpadding=\"3\"><tr><td align=\"" + align + "\">"
                 + head + "<br>"
                 + "<table bgcolor=\"" + bub + "\" cellpadding=\"6\" cellspacing=\"0\"><tr><td>"
                 + "<font color=\"" + Theme.BUBTX + "\">" + m.body + "</font>"
                 + "</td></tr></table></td></tr></table>";
        }
        public void renderAll() {
            setBackground(Theme.chatBgColor());
            StringBuffer sb = new StringBuffer("<html><body style=\"background-color:")
                .append(Theme.chatBg()).append(";font-family:'Microsoft YaHei',sans-serif;font-size:13px\">");
            for(int i=0;i<msgs.size();i++)
                sb.append(Theme.apply(renderMsg((Msg)msgs.get(i))));
            sb.append("</body></html>");
            setText(sb.toString());
            try { setCaretPosition(getDocument().getLength()); } catch(Exception e) {}
        }
        public void clear() {
            msgs.clear();
            keyIdx.clear();
            renderAll();
        }
    }
}

