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
        //创建North
        northPanel = new JPanel(new GridLayout(0,2));
        northPanel.add(new JLabel("Host address:"));
        northPanel.add(txtHost = new JTextField(ChatClient.serverText));
        northPanel.add(new JLabel("Port:"));
        northPanel.add(txtPort = new JTextField(ChatClient.portText));
        northPanel.add(new JLabel("Nick:"));
        northPanel.add(txtNick = new JTextField(ChatClient.nickText));
        northPanel.add(new JLabel(""));
        northPanel.add(new JLabel(""));
        northPanel.add(new JLabel(""));
        northPanel.add(buttonConnect = new JButton("Connect"));
        buttonConnect.addActionListener(this);
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
                // 点列表空白处取消选中（选中=文件私发对象，取消=群发）
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
        } else if(cmd.startsWith("PM ")) {
            String rest = cmd.substring(3);
            int p = rest.indexOf(' ');
            if(p < 0) return;
            // 对方发来的私聊 → 路由进与发送者的会话窗格
            appendTo(rest.substring(0, p), rest.substring(0, p) + ": " + rest.substring(p + 1));
        } else if(cmd.startsWith("PMSENT ")) {
            String rest = cmd.substring(7);
            int p = rest.indexOf(' ');
            if(p < 0) return;
            // 我发出的私聊回显 → 同一会话窗格,灰色区分
            appendTo(rest.substring(0, p), "<font color=\"#888888\">我: " + rest.substring(p + 1) + "</font>");
        } else if(cmd.startsWith("FILEP ")) {
            receiveFile(cmd.substring(6), null); // null=按发送者路由进私聊会话
        } else if(cmd.startsWith("FILE ")) {
            receiveFile(cmd.substring(5), MAIN_ROOM); // 群发文件进公共聊天室
        } else if(cmd.startsWith("USERINFO ")) {
            showUserInfo(cmd.substring(9));
        }
    }
    // 接收文件：解码 Base64 存到 用户主目录\ChatDownloads\，图片内嵌显示。
    // route=目标会话窗格；传 null 表示私发文件，按发送者路由进对应私聊会话
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
            java.io.File dir = new java.io.File(System.getProperty("user.home"), "ChatDownloads");
            dir.mkdirs();
            java.io.File out = new java.io.File(dir, fname);
            if(out.exists()) out = new java.io.File(dir, System.currentTimeMillis() + "_" + fname);
            java.nio.file.Files.write(out.toPath(), data);
            String lower = fname.toLowerCase();
            String uri = "" + out.toURI();
            if(lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
                // 聊天区是 HTML 渲染，图片用 <img> 内嵌显示（文档要求⑥）。
                // 按 240px 上限等比缩小成缩略图，避免大图撑爆聊天区；点链接看原图
                String size = imgSizeAttr(out);
                appendTo(pane, "<font color=\"#9933cc\">[图片] 来自 " + sender + ": " + fname
                        + "</font>（<a href=\"" + uri + "\">查看原图</a>）<br><img src=\"" + uri + "\"" + size + ">");
            } else if(lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")
                || lower.endsWith(".mov") || lower.endsWith(".wmv")) {
                // 视频不做内嵌播放（JEditorPane 无 video 能力），点击调系统默认播放器
                appendTo(pane, "<font color=\"#9933cc\">[视频] 来自 " + sender + ": " + fname
                        + "（已保存，<a href=\"" + uri + "\">点击播放</a>）</font>");
            } else {
                appendTo(pane, "<font color=\"#9933cc\">[文件] 来自 " + sender + ": " + fname
                        + "（已保存到 " + out.getAbsolutePath() + "，<a href=\"" + uri + "\">打开</a>）</font>");
            }
        } catch(Exception ex) {
            appendTo(MAIN_ROOM, "<font color=\"#ff0000\">接收文件失败: " + ex.getMessage() + "</font>");
        }
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
    void showSentFile(String target, java.io.File f, String fname) {
        String pane = "*".equals(target) ? MAIN_ROOM : target;
        String uri = "" + f.toURI();
        String head = "<font color=\"#888888\">我" + ("*".equals(target) ? "(群发)" : "") + ": </font>";
        String lower = fname.toLowerCase();
        if(lower.endsWith(".png") || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
            appendTo(pane, head + "<font color=\"#9933cc\">[图片] " + fname
                    + "</font>（<a href=\"" + uri + "\">查看原图</a>）<br><img src=\"" + uri + "\"" + imgSizeAttr(f) + ">");
        } else if(lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")
            || lower.endsWith(".mov") || lower.endsWith(".wmv")) {
            appendTo(pane, head + "<font color=\"#9933cc\">[视频] " + fname
                    + "（<a href=\"" + uri + "\">点击播放</a>）</font>");
        } else {
            appendTo(pane, head + "<font color=\"#9933cc\">[文件] " + fname
                    + "（<a href=\"" + uri + "\">打开</a>）</font>");
        }
    }
    // 渲染 USERINFO 推送到右侧信息栏，格式："用户 字段: 值|字段: 值"
    private void showUserInfo(String rest) {
        int p = rest.indexOf(' ');
        String user = (p < 0) ? rest : rest.substring(0, p);
        if(MAIN_ROOM.equals(currentConv) || !user.equalsIgnoreCase(currentConv)) return; // 只更新当前会话对象
        String html = "<b>" + user + "</b><br>";
        html += containsIgnoreCase(online, user) ? "<font color=\"#00aa00\">[在线]</font><br>"
                                                 : "<font color=\"#888888\">[离线]</font><br>";
        if(p < 0) html += "<br>暂无个人信息";
        else {
            StringTokenizer st = new StringTokenizer(rest.substring(p + 1), "|");
            while(st.hasMoreTokens()) html += "<br>" + st.nextToken();
        }
        infoPane.setText(html);
    }
    // 收到服务器改名成功回执时，把最新昵称同步到顶部 Nick 框。
    // 只认以 "Server:" 开头的消息：聊天广播都带 "昵称:" 前缀，不会被别人发的内容伪造
    private void syncNick(String str) {
        String marker = "ou are now known as "; // 兼容 "You are..." 和 "...you are..." 两种回执
        int i = str.indexOf(marker);
        if(str.startsWith("Server:") && i > 0)
            txtNick.setText(str.substring(i + marker.length()).trim());
    }
    private void connect() {
        try {
            if(ck!=null) ck.dropMe();
            ck = new ClientKernel(txtHost.getText(), Integer.parseInt(txtPort.getText()));
            // 昵称框还是占位符 "YourName" 时不自动改名，否则多个客户端会互相抢注同一个名字
            boolean nickSet = !txtNick.getText().equals(ChatClient.nickText)
                              && txtNick.getText().trim().length() > 0;
            if(nickSet) ck.setNick(txtNick.getText());
            if(ck.isConnected()) {
                ck.addClient(this);
                addMsg("<font color=\"#00ff00\">connected! Local Port:" + ck.getLocalPort() + "</font>");
                if(!nickSet) addMsg("提示：昵称框未填写，当前昵称为端口号，可在上方 Nick 框输入名字后回车，或发送 /nick 名字 修改");
            } else {
                addMsg("<font color=\"#ff0000\">connect failed！</font>");
            }
        } catch(Exception e) { e.printStackTrace(); }
    }
    private void send() {
        if(ck == null || !ck.isConnected()) { // 未连接就点 Send：原代码此处 NPE 崩溃
            addMsg("<font color=\"#ff0000\">尚未连接服务器，请先点击 Connect</font>");
            return;
        }
        String toSend = msgWindow.getText();
        if(toSend == null || toSend.trim().length() == 0) return;
        // 会话感知：私聊会话里输入的普通文字自动转为 /msg 对方 内容；命令(/开头)原样发
        if(!MAIN_ROOM.equals(currentConv) && !toSend.startsWith("/"))
            ck.sendMessage("/msg " + currentConv + " " + toSend);
        else
            ck.sendMessage(toSend);
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
        if(e.getSource()==buttonAddFriend && !MAIN_ROOM.equals(currentConv)
            && ck != null && ck.isConnected())
            ck.sendMessage("/addfriend " + currentConv); // 右栏一键加好友，回执进公共聊天室
    }
    // 发送文件/图片：右侧选中了人=私发给他，没选人=群发给所有人
    private void sendFile() {
        if(ck == null || !ck.isConnected()) {
            addMsg("<font color=\"#ff0000\">尚未连接服务器，请先点击 Connect</font>");
            return;
        }
        // 私聊会话里发文件=发给会话对方；公共聊天室里按右侧选中（无选中=群发）
        String target;
        if(!MAIN_ROOM.equals(currentConv)) {
            target = currentConv;
        } else {
            Object sel = userList.getSelectedValue();
            target = (sel == null) ? "*" : sel.toString();
        }
        if(target.equalsIgnoreCase(txtNick.getText())) {
            addMsg("不能给自己发文件，请选择其他用户，或取消选择进行群发");
            return;
        }
        JFileChooser fc = new JFileChooser();
        if(fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        if(f.length() > 20*1024*1024) { // 放宽到 20MB 以容纳短视频；单通道设计，传输期间该用户的聊天会短暂排队
            addMsg("<font color=\"#ff0000\">文件过大（限 20MB）：" + f.getName() + "</font>");
            return;
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            String b64 = Base64.getEncoder().encodeToString(data);
            String fname = f.getName().replace(' ', '_'); // 协议按空格分隔字段，文件名里的空格转下划线
            ck.sendFile(target, fname, b64);
            showSentFile(target, f, fname); // 自己这端也立刻可见/可打开(引用本地原文件)
        } catch(Exception ex) {
            addMsg("<font color=\"#ff0000\">读取文件失败: " + ex.getMessage() + "</font>");
        }
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
    class ClientHistory extends JEditorPane {
        public ClientHistory() {
            super("text/html", "" + ChatClient.appName);
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
        }
        public void addText(String str) {
            String html = getText();
            int end = html.lastIndexOf("</body>");
            String startStr = html.substring(0, end);
            String endStr = html.substring(end, html.length());
            String newHtml = startStr + "<br>" + str + endStr;
            setText(newHtml);
            setSelectionStart(newHtml.length()-1);
            setSelectionEnd(newHtml.length());
         }
        public void clear() {
            setText("");
        }
    }
}

