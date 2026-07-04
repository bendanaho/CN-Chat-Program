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
    String myNick = "";                // 服务器确认的"我的昵称",判断消息归属用(比可编辑的 txtNick 可靠)
    HashMap recvs = new HashMap();     // 分块接收中的文件:tid -> FileRecv(功能十二)
    private static int tidCounter = 0;
    private String rawInfoHtml = "";   // 右栏信息的原始(带颜色占位符)HTML,切主题时重渲染
    Vector rooms = new Vector();        // 我加入的群组(功能十七)
    HashMap sentPm = new HashMap();     // 我发出的私聊 id -> {会话, 正文},收到 READ 时更新已读(功能十九)
    HashMap lastRecvId = new HashMap(); // 会话 -> 最近收到的对方私聊消息 id(打开会话时回执,功能十九)
    JButton buttonRoom;                 // 加入/创建群组
    JLabel statusBar, typingLabel, peerTitle; // 状态栏/正在输入/右栏标题(对方信息↔群成员)
    long lastTypingSent = 0;            // /typing 节流
    javax.swing.Timer typingClearTimer; // 定时清除"对方正在输入"
    // 本地聊天记录(功能二十一):按登录身份隔离,每个昵称一个子目录,每会话一个文件
    final java.io.File historyDir = new java.io.File(System.getProperty("user.home"), ".chattool-history");
    String historyOwner = "";          // 当前记录归属的身份(=登录昵称);空=未登录,不读写
    // 头像(功能二十二)
    HashMap avatars = new HashMap();   // 昵称(小写) -> ImageIcon(20px)
    final java.io.File avatarDir = new java.io.File(System.getProperty("user.home"), ".chattool-avatars");
    JButton buttonAvatar;
    // 语音(功能二十三)
    JButton buttonVoice;
    javax.sound.sampled.TargetDataLine recLine;
    java.io.ByteArrayOutputStream recBuf;
    volatile boolean recording = false;
    long recStart;
    // 通知(功能二十四)
    JCheckBox soundToggle;
    java.awt.TrayIcon trayIcon;
    // 表情(功能二十六)
    JButton buttonEmote;
    // 注册/登录(账号体系 UI 化)
    JButton buttonRegister;
    boolean awaitingVerify = false; // 已发 /nick 到注册名,正等服务器要求验证
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
        JPanel connFields = new JPanel(new GridLayout(0,2,4,4));
        connFields.add(new JLabel(" 服务器:"));
        connFields.add(txtHost = new JTextField(ChatClient.serverText));
        connFields.add(new JLabel(" 端口:"));
        connFields.add(txtPort = new JTextField(ChatClient.portText));
        connFields.add(new JLabel(" 昵称:"));
        connFields.add(txtNick = new JTextField(ChatClient.nickText));
        JPanel connBtns = new JPanel(new GridLayout(1,0,4,4));
        connBtns.add(buttonScan = new JButton("扫描局域网"));
        connBtns.add(buttonConnect = new JButton("连接"));
        connBtns.add(buttonRegister = new JButton("注册"));
        buttonRegister.addActionListener(this);
        JPanel connPanel = new JPanel(new BorderLayout(0,4));
        connPanel.setBorder(titled("连接 / 注册"));
        connPanel.add(connFields, BorderLayout.CENTER);
        connPanel.add(connBtns, BorderLayout.SOUTH);
        connPanel.setPreferredSize(new Dimension(300, 0));
        // 个人资料:字段/内容两列的可编辑表格(功能五 UI 化)
        infoModel = new DefaultTableModel(new Object[]{"字段","内容"}, 0);
        infoTable = new JTable(infoModel);
        infoTable.setRowHeight(22);
        infoTable.getTableHeader().setReorderingAllowed(false);
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(titled("个人资料(双击单元格编辑;内容留空并保存=删除该项)"));
        infoPanel.add(new JScrollPane(infoTable), BorderLayout.CENTER);
        JPanel infoBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        infoBtns.add(buttonAvatar = new JButton("设置头像"));
        infoBtns.add(buttonAddRow = new JButton("＋ 加一项"));
        infoBtns.add(buttonSaveInfo = new JButton("保存资料"));
        buttonAvatar.addActionListener(this);
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
        //创建South:上="对方正在输入"提示,中=输入框,右=按钮
        southPanel = new JPanel(new BorderLayout(4, 0));
        southPanel.setBorder(BorderFactory.createEmptyBorder(2,6,4,6));
        typingLabel = new JLabel(" ");
        typingLabel.setForeground(Color.gray);
        typingLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        msgWindow = new JTextField();
        msgWindow.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        JPanel sendBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        sendBtns.add(buttonEmote = new JButton("表情"));
        sendBtns.add(buttonVoice = new JButton("按住说话"));
        sendBtns.add(buttonFile = new JButton("文件"));
        sendBtns.add(buttonSend = new JButton("发送"));
        buttonEmote.addActionListener(this);
        // 语音:按下开始录音,松开发送(功能二十三)
        buttonVoice.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { startRecording(); }
            public void mouseReleased(MouseEvent e) { stopRecordingAndSend(); }
        });
        southPanel.add(typingLabel, BorderLayout.NORTH);
        southPanel.add(msgWindow, BorderLayout.CENTER);
        southPanel.add(sendBtns, BorderLayout.EAST);
        buttonSend.addActionListener(this);
        buttonFile.addActionListener(this);
        msgWindow.addKeyListener(this);
        // 网络状态栏(功能十五) + 提示音开关(功能二十四):贴在整个窗口最底部
        statusBar = new JLabel(" 未连接");
        statusBar.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        statusBar.setBorder(BorderFactory.createEmptyBorder(1,6,1,6));
        soundToggle = new JCheckBox("提示音", true);
        soundToggle.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusBar, BorderLayout.CENTER);
        statusPanel.add(soundToggle, BorderLayout.EAST);
        this.add(statusPanel, BorderLayout.SOUTH);
        new javax.swing.Timer(1000, new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                statusBar.setText(ck != null && ck.isConnected() ? ck.statsLine()
                        : (reconnecting ? "  连接已断开,自动重连中..." : "  未连接"));
            }
        }).start();
        //创建Center：CardLayout，每个会话一个独立聊天窗格
        centerLayout = new CardLayout();
        centerCards = new JPanel(centerLayout);
        historyWindow = new ClientHistory(); // 公共聊天室窗格
        historyWindow.convName = MAIN_ROOM;
        convs.put(MAIN_ROOM, historyWindow);
        sc = new JScrollPane(historyWindow);
        sc.setAutoscrolls(true);
        centerCards.add(sc, MAIN_ROOM);
        // 输入栏放进聊天区容器的底部,左右边界与聊天详情对齐(不再横跨整个窗口底部)
        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.add(centerCards, BorderLayout.CENTER);
        centerWrap.add(southPanel, BorderLayout.SOUTH);
        this.add(centerWrap, BorderLayout.CENTER);
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
        peerPanel.add(peerTitle = new JLabel("对方信息", JLabel.CENTER), BorderLayout.NORTH);
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
        JPanel westPanel = new JPanel(new BorderLayout());
        buttonRoom = new JButton("＋ 加入/创建群组");
        buttonRoom.addActionListener(this);
        westPanel.add(buttonRoom, BorderLayout.NORTH);
        westPanel.add(new JScrollPane(convList), BorderLayout.CENTER);
        westPanel.setPreferredSize(new Dimension(150, 0));
        this.add(westPanel, BorderLayout.WEST);
        rebuildConvList();
        // 头像渲染器(功能二十二):在线用户与会话列表的名字前显示头像
        userList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList l, Object v, int idx, boolean sel, boolean foc) {
                JLabel lb = (JLabel)super.getListCellRendererComponent(l, v, idx, sel, foc);
                Object ic = (v == null) ? null : avatars.get(v.toString().toLowerCase());
                lb.setIcon(ic == null ? null : (Icon)ic);
                return lb;
            }
        });
        convList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList l, Object v, int idx, boolean sel, boolean foc) {
                JLabel lb = (JLabel)super.getListCellRendererComponent(l, v, idx, sel, foc);
                Icon ic = null;
                if(idx >= 0 && idx < itemNames.size()) {
                    Object name = itemNames.get(idx);
                    if(name != null && !name.toString().startsWith("#")) {
                        Object o = avatars.get(name.toString().toLowerCase());
                        if(o != null) ic = (Icon)o;
                    }
                }
                lb.setIcon(ic);
                return lb;
            }
        });
        loadAvatarCache();
        // 拖拽发送(功能二十五):把文件拖进聊天区即发送到当前会话
        new java.awt.dnd.DropTarget(centerCards, new java.awt.dnd.DropTargetAdapter() {
            public void drop(java.awt.dnd.DropTargetDropEvent ev) {
                try {
                    ev.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                    java.util.List fs = (java.util.List)ev.getTransferable()
                        .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    if(ck == null || !ck.isConnected()) { addMsg("<font color=\"" + Theme.ERR + "\">请先连接服务器</font>"); return; }
                    String target = MAIN_ROOM.equals(currentConv) ? "*" : currentConv;
                    for(int i=0;i<fs.size();i++) {
                        java.io.File f = (java.io.File)fs.get(i);
                        if(f.isFile() && f.length() <= 200L*1024*1024) sendFileChunked(target, f);
                    }
                } catch(Exception ex) {}
            }
        });
        setupTray(); // 系统托盘(功能二十四)
        applyTheme();     // 启动即按用户上次选择的主题渲染
    }
    // ===== 系统托盘(功能二十四):最小化隐藏到托盘,来消息气泡提示,单击恢复 =====
    private void setupTray() {
        if(!java.awt.SystemTray.isSupported()) return;
        try {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(16, 16,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            g.setColor(new Color(0x07, 0xc1, 0x60));
            g.fillRect(0, 0, 16, 16);
            g.setColor(Color.white);
            g.drawString("C", 4, 12);
            g.dispose();
            trayIcon = new java.awt.TrayIcon(im, "Chat Tool");
            trayIcon.setImageAutoSize(true);
            // 双击托盘图标恢复窗口
            trayIcon.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { showFromTray(); }
            });
            // 托盘右键菜单:显示主窗口 / 退出
            java.awt.PopupMenu pop = new java.awt.PopupMenu();
            java.awt.MenuItem show = new java.awt.MenuItem("显示主窗口");
            show.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { showFromTray(); }
            });
            java.awt.MenuItem exit = new java.awt.MenuItem("退出");
            exit.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { System.exit(0); }
            });
            pop.add(show); pop.addSeparator(); pop.add(exit);
            trayIcon.setPopupMenu(pop);
            java.awt.SystemTray.getSystemTray().add(trayIcon);
            // 说明:不再"最小化即藏进托盘"——最小化就正常留在任务栏,避免窗口神秘消失。
            // 托盘图标常驻,仅用于后台通知气泡与右键菜单。
        } catch(Exception e) {}
    }
    private void showFromTray() { setVisible(true); setState(JFrame.NORMAL); toFront(); requestFocus(); }
    // 新消息注意力提示(功能二十四):提示音 + 未聚焦时任务栏闪烁/托盘气泡;@提及强提醒
    private void notifyIncoming(String sender, String body, boolean mention) {
        if(sender != null && isMe(sender)) return; // 自己发的不提醒
        boolean focused = isFocused();
        if(soundToggle.isSelected() && (!focused || mention)) Toolkit.getDefaultToolkit().beep();
        if(!focused) {
            toFront(); // Windows 下无法抢焦点时表现为任务栏闪烁
            if(trayIcon != null) // 后台(未聚焦)时弹托盘气泡
                trayIcon.displayMessage("新消息", sender + ": " + body.replaceAll("<[^>]*>", ""),
                    java.awt.TrayIcon.MessageType.INFO);
        }
    }
    // 窗口抖动(功能二十四):被抖时窗口小幅震动约半秒
    void doShake() {
        final Point p = getLocation();
        final int[] n = {0};
        final javax.swing.Timer t = new javax.swing.Timer(20, null);
        t.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                n[0]++;
                setLocation(p.x + ((n[0] % 4 < 2) ? 6 : -6), p.y + ((n[0] % 2 == 0) ? 4 : -4));
                if(n[0] > 24) { t.stop(); setLocation(p); }
            }
        });
        t.start();
    }
    // ===== 头像(功能二十二) =====
    private void loadAvatarCache() { // 启动时从本地缓存恢复(离线也能显示已知头像)
        java.io.File[] fs = avatarDir.listFiles();
        if(fs == null) return;
        for(int i=0;i<fs.length;i++) {
            String fn = fs[i].getName();
            if(!fn.endsWith(".png")) continue;
            try {
                String nick = new String(java.util.Base64.getUrlDecoder()
                    .decode(fn.substring(0, fn.length()-4)), "UTF-8");
                avatars.put(nick.toLowerCase(), scaledIcon(fs[i]));
            } catch(Exception e) {}
        }
    }
    private ImageIcon scaledIcon(java.io.File f) throws Exception {
        Image im = javax.imageio.ImageIO.read(f);
        return new ImageIcon(im.getScaledInstance(22, 22, Image.SCALE_SMOOTH)); // 平滑缩放,列表小图标
    }
    // 某昵称的头像文件;没有则返回默认头像(灰色人形,首次生成)——供气泡旁显示
    java.io.File avatarFileOf(String nick) {
        if(nick != null) {
            java.io.File f = new java.io.File(avatarDir, b64(nick.toLowerCase()) + ".png");
            if(f.exists()) return f;
        }
        java.io.File d = new java.io.File(avatarDir, "_default.png");
        if(!d.exists()) {
            try {
                avatarDir.mkdirs();
                java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(48, 48,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
                Graphics2D g = im.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0xb8, 0xc0, 0xc8));
                g.fillRect(0, 0, 48, 48);
                g.setColor(Color.white);
                g.fillOval(15, 8, 18, 18);   // 头
                g.fillOval(8, 28, 32, 26);   // 肩
                g.dispose();
                javax.imageio.ImageIO.write(im, "png", d);
            } catch(Exception e) {}
        }
        return d;
    }
    // 句首是否为表情短码(如 /微笑):是则按聊天发送而不是命令(功能二十六修复)
    private boolean startsWithEmote(String s) {
        for(int i = 0; i < Emotes.NAMES.length; i++)
            if(s.startsWith("/" + Emotes.NAMES[i])) return true;
        return false;
    }
    // 选图→缩放到 48x48 PNG→Base64 上传(几 KB,单条消息即可承载)
    private void chooseAvatar() {
        if(ck == null || !ck.isConnected()) { addMsg("<font color=\"" + Theme.ERR + "\">请先连接服务器</font>"); return; }
        JFileChooser fc = new JFileChooser();
        if(fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Image src = javax.imageio.ImageIO.read(fc.getSelectedFile());
            if(src == null) { addMsg("<font color=\"" + Theme.ERR + "\">不是有效的图片文件</font>"); return; }
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(128, 128,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, 128, 128, null);
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "png", baos);
            ck.sendMessage("/avatar " + Base64.getEncoder().encodeToString(baos.toByteArray()));
        } catch(Exception ex) {
            addMsg("<font color=\"" + Theme.ERR + "\">设置头像失败: " + ex.getMessage() + "</font>");
        }
    }
    // ===== 语音(功能二十三):按住录音(16kHz 16bit 单声道),松开存 WAV 走分块传输 =====
    private void startRecording() {
        if(ck == null || !ck.isConnected() || recording) return;
        try {
            javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(16000, 16, 1, true, false);
            javax.sound.sampled.DataLine.Info info =
                new javax.sound.sampled.DataLine.Info(javax.sound.sampled.TargetDataLine.class, fmt);
            recLine = (javax.sound.sampled.TargetDataLine)javax.sound.sampled.AudioSystem.getLine(info);
            recLine.open(fmt);
            recLine.start();
            recBuf = new java.io.ByteArrayOutputStream();
            recording = true;
            recStart = System.currentTimeMillis();
            buttonVoice.setText("松开发送");
            new Thread() { public void run() {
                byte[] buf = new byte[3200];
                while(recording && System.currentTimeMillis() - recStart < 60000) { // 上限 60 秒
                    int n = recLine.read(buf, 0, buf.length);
                    if(n > 0) recBuf.write(buf, 0, n);
                }
            }}.start();
        } catch(Exception ex) {
            addMsg("<font color=\"" + Theme.ERR + "\">无法录音(没有麦克风?): " + ex.getMessage() + "</font>");
        }
    }
    private void stopRecordingAndSend() {
        if(!recording) return;
        recording = false;
        buttonVoice.setText("按住说话");
        try {
            recLine.stop();
            recLine.close();
            Thread.sleep(80); // 等采集线程退出
            byte[] pcm = recBuf.toByteArray();
            if(System.currentTimeMillis() - recStart < 600 || pcm.length == 0) {
                addMsg("<font color=\"" + Theme.SELF + "\">录音太短,已取消</font>");
                return;
            }
            javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(16000, 16, 1, true, false);
            javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(
                new java.io.ByteArrayInputStream(pcm), fmt, pcm.length / fmt.getFrameSize());
            java.io.File f = new java.io.File(System.getProperty("java.io.tmpdir"),
                "voice_" + new java.text.SimpleDateFormat("HHmmss").format(new java.util.Date()) + ".wav");
            javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, f);
            sendFileChunked(MAIN_ROOM.equals(currentConv) ? "*" : currentConv, f);
        } catch(Exception ex) {
            addMsg("<font color=\"" + Theme.ERR + "\">语音发送失败: " + ex.getMessage() + "</font>");
        }
    }
    // ===== 表情面板(功能二十六):内置表情插短码;自定义表情点击直接作为图片发送 =====
    private void showEmoteMenu() {
        JPopupMenu menu = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
        for(int i = 0; i < Emotes.NAMES.length; i++) {
            final String name = Emotes.NAMES[i];
            JButton b = new JButton(new ImageIcon(Emotes.file(name).getAbsolutePath()));
            b.setToolTipText("/" + name);
            b.setMargin(new Insets(2,2,2,2));
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    msgWindow.setText(msgWindow.getText() + "/" + name);
                    msgWindow.requestFocus();
                    menuHide();
                }
            });
            grid.add(b);
        }
        java.io.File[] cs = Emotes.customs();
        for(int i = 0; i < cs.length; i++) {
            final java.io.File cf = cs[i];
            try {
                Image im = javax.imageio.ImageIO.read(cf);
                if(im == null) continue;
                JButton b = new JButton(new ImageIcon(im.getScaledInstance(28, 28, Image.SCALE_SMOOTH)));
                b.setToolTipText("自定义表情(点击发送)");
                b.setMargin(new Insets(2,2,2,2));
                b.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) {
                        if(ck != null && ck.isConnected())
                            sendFileChunked(MAIN_ROOM.equals(currentConv) ? "*" : currentConv, cf);
                        menuHide();
                    }
                });
                grid.add(b);
            } catch(Exception e) {}
        }
        JButton add = new JButton("＋");
        add.setToolTipText("添加自定义表情(选一张图片)");
        add.setMargin(new Insets(2,6,2,6));
        add.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                menuHide();
                JFileChooser fc = new JFileChooser();
                if(fc.showOpenDialog(ChatClient.this) == JFileChooser.APPROVE_OPTION) {
                    try { Emotes.addCustom(fc.getSelectedFile()); }
                    catch(Exception ex) { addMsg("<font color=\"" + Theme.ERR + "\">添加失败</font>"); }
                }
            }
        });
        grid.add(add);
        menu.add(grid);
        emoteMenu = menu;
        menu.show(buttonEmote, 0, -menu.getPreferredSize().height);
    }
    private JPopupMenu emoteMenu;
    private void menuHide() { if(emoteMenu != null) emoteMenu.setVisible(false); }
    // ===== 本地聊天记录(功能二十一,按身份隔离) =====
    private String b64(String s) {
        try { return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes("UTF-8")); }
        catch(Exception e) { return "x"; }
    }
    private java.io.File ownerDir(String owner) { return new java.io.File(historyDir, b64(owner)); }
    private java.io.File histFile(String conv) { return new java.io.File(ownerDir(historyOwner), b64(conv) + ".txt"); }
    // 某会话有新消息/更新时,把它的全部记录重写落盘(未登录则不写)
    void saveHistory(String conv, ClientHistory h) {
        if(conv == null || historyOwner.length() == 0) return;
        try {
            ownerDir(historyOwner).mkdirs();
            String s = h.serialize();
            java.io.File f = histFile(conv);
            if(s.length() == 0) { if(f.exists()) f.delete(); return; }
            java.io.BufferedWriter w = new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), "UTF-8"));
            w.write(s); w.close();
        } catch(Exception e) {}
    }
    // 登录后加载"我这个身份"的历史;身份切换时先清空当前面板再载入
    private void loadOwnerHistory() {
        if(myNick.length() == 0 || myNick.equals(historyOwner)) return; // 同身份不重复加载
        // 切换身份:清空所有会话面板与临时会话,避免上一身份的记录残留
        Iterator it = convs.values().iterator();
        while(it.hasNext()) ((ClientHistory)(it.next())).clear();
        tempConvs.clear();
        historyOwner = myNick;
        java.io.File[] fs = ownerDir(historyOwner).listFiles();
        if(fs != null) for(int i=0;i<fs.length;i++) {
            String fn = fs[i].getName();
            if(!fn.endsWith(".txt")) continue;
            try {
                String conv = new String(java.util.Base64.getUrlDecoder()
                        .decode(fn.substring(0, fn.length()-4)), "UTF-8");
                ClientHistory h = ensureConv(conv);
                java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(fs[i]), "UTF-8"));
                String line;
                while((line = r.readLine()) != null) h.addLoaded(line);
                r.close();
                h.renderAll();
            } catch(Exception e) {}
        }
        rebuildConvList();
    }
    private void clearHistory(String conv) {
        ClientHistory h = (ClientHistory)convs.get(conv);
        if(h != null) h.clear();
        java.io.File f = histFile(conv);
        if(f.exists()) f.delete();
    }
    // 统一风格的分组标题边框
    private Border titled(String t) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Theme.panelColor().darker()), t);
    }
    private void setThemeButtonText() {
        // 图标放大到 30pt、文字放大到 16pt
        buttonTheme.setText("<html><center><font size=\"7\">" + (Theme.isDark() ? "☀" : "☾")
                + "</font><br><font size=\"5\">" + (Theme.isDark() ? "日间" : "夜间") + "</font></center></html>");
        buttonTheme.setToolTipText("切换日间/夜间模式");
    }
    // ===== 主题(功能十四):遍历组件树刷色 + 各会话窗格整体重渲染 =====
    private void applyTheme() {
        themeWalk(getContentPane());
        Iterator it = convs.values().iterator();
        while(it.hasNext()) ((ClientHistory)(it.next())).renderAll();
        infoPane.setText(Theme.apply(rawInfoHtml));
        infoPane.setBackground(Theme.bgColor());
        // 主题按钮:普通面板背景色(去掉绿色底),靠大图标+大字醒目
        buttonTheme.setBackground(Theme.panelColor());
        buttonTheme.setForeground(Theme.fgColor());
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
        // 注册成功 = 以该名登录:更新权威昵称、载入该身份历史(此消息不含"known as",syncNick 不覆盖)
        if(str.indexOf("is now registered and set as your own") >= 0 && str.startsWith("User ")) {
            int b = str.indexOf(" is now registered");
            if(b > 5) {
                myNick = str.substring(5, b);
                txtNick.setText(myNick);
                loadOwnerHistory();
                if(ck != null && ck.isConnected()) ck.sendMessage("/infoq " + myNick);
            }
        }
        // 自动重连取回注册昵称:服务器要求验证且缓存过密码 → 自动 /verify(功能十一)
        if(str.indexOf("is registered so you have to verify") >= 0 && ck != null && ck.isConnected()) {
            if(lastPassword != null) { // 有缓存密码(如自动重连)→ 直接验证
                ck.sendMessage("/verify " + lastPassword);
                appendTo(MAIN_ROOM, "<font color=\"" + Theme.SELF + "\">已用缓存的密码自动验证身份...</font>");
            } else { // 无缓存 → 弹密码框登录
                String nk = txtNick.getText().trim();
                int a = str.indexOf("Nick ");
                if(a == 0) { int b = str.indexOf(" is registered"); if(b > 5) nk = str.substring(5, b); }
                promptVerify(nk);
            }
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
            boolean mine = isMe(t[1]);
            // @提及(功能二十四):被点名时正文橙色加粗 + 强提醒
            boolean mention = !mine && myNick.length() > 0
                && t[2].toLowerCase().indexOf("@" + myNick.toLowerCase()) >= 0;
            String body2 = mention ? "<b><font color=\"" + Theme.WARN + "\">" + t[2] + "</font></b>" : t[2];
            ensureConv(MAIN_ROOM).putMsg("m" + t[0], mine ? ClientHistory.SELF : ClientHistory.OTHER, t[1], body2);
            markUnread(MAIN_ROOM);
            notifyIncoming(t[1], t[2], mention);
        } else if(cmd.startsWith("PM ")) {
            // 私聊: PM <id> <发送者> <正文> → 对方的消息,靠左气泡
            String[] t = split3(cmd.substring(3));
            if(t == null) return;
            ensureConv(t[1]).putMsg("m" + t[0], ClientHistory.OTHER, t[1], t[2]);
            notifyIncoming(t[1], t[2], false);
            lastRecvId.put(t[1], t[0]);          // 记住最近收到的 id,供已读回执
            if(t[1].equals(currentConv) && ck != null && ck.isConnected())
                ck.sendMessage("/read " + t[0]); // 正在看这个会话 → 立即回执已读
            markUnread(t[1]);
        } else if(cmd.startsWith("PMSENT ")) {
            // 我发出的私聊回显: PMSENT <id> <目标> <正文> → 靠右气泡,附"已送达"
            String[] t = split3(cmd.substring(7));
            if(t == null) return;
            sentPm.put(t[0], new String[]{ t[1], t[2] }); // 存正文,收到 READ 时改为"已读"
            ensureConv(t[1]).putMsg("m" + t[0], ClientHistory.SELF, txtNick.getText().trim(),
                    t[2] + " <font size=\"2\" color=\"" + Theme.TIME + "\">· 已送达</font>");
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
        } else if(cmd.startsWith("AVATAR ")) {
            // 头像分发(功能二十二):缓存到本地 + 刷新列表图标
            int sp = cmd.indexOf(' ', 7);
            if(sp < 0) return;
            String nick = cmd.substring(7, sp);
            try {
                byte[] png = Base64.getDecoder().decode(cmd.substring(sp + 1));
                avatarDir.mkdirs();
                java.io.File f = new java.io.File(avatarDir, b64(nick.toLowerCase()) + ".png");
                java.nio.file.Files.write(f.toPath(), png);
                avatars.put(nick.toLowerCase(), scaledIcon(f));
                userList.repaint();
                convList.repaint();
                // 气泡旁头像即时生效:重渲染所有会话窗格
                Iterator hit = convs.values().iterator();
                while(hit.hasNext()) ((ClientHistory)(hit.next())).renderAll();
            } catch(Exception e) {}
        } else if(cmd.startsWith("SHAKE ")) {
            // 窗口抖动(功能二十四)
            String from = cmd.substring(6).trim();
            appendTo(from, "<font color=\"" + Theme.WARN + "\">" + from + " 向你发送了一个窗口抖动</font>");
            if(soundToggle.isSelected()) Toolkit.getDefaultToolkit().beep();
            doShake();
        } else if(cmd.startsWith("NEEDPASS")) {
            promptEntryPass();
        } else if(cmd.startsWith("ROOMMEM ")) {
            // 群成员列表: ROOMMEM <房间> <成员...> → 若正看该群,渲染进右栏
            int sp = cmd.indexOf(' ', 8);
            String room = (sp < 0) ? cmd.substring(8) : cmd.substring(8, sp);
            if(("#" + room).equals(currentConv)) {
                String html = "<b>群 " + room + "</b><br>";
                if(sp > 0) {
                    StringTokenizer st = new StringTokenizer(cmd.substring(sp + 1));
                    while(st.hasMoreTokens()) html += "<br>· " + st.nextToken();
                } else html += "<br>(空)";
                rawInfoHtml = html;
                infoPane.setText(Theme.apply(html));
            }
        } else if(cmd.equals("ROOMS") || cmd.startsWith("ROOMS ")) {
            // 精确匹配:否则 "ROOMSYS ..."(以 ROOMS 开头)会被误当成群组列表
            rooms.clear();
            StringTokenizer st = new StringTokenizer(cmd.length() > 5 ? cmd.substring(5) : "");
            while(st.hasMoreTokens()) rooms.add(st.nextToken());
            // 若当前正看的群组已不在我的群组列表(被移出/退出)→ 回到公共聊天室
            if(currentConv.startsWith("#") && !rooms.contains(currentConv.substring(1)))
                openConv(MAIN_ROOM);
            rebuildConvList();
        } else if(cmd.startsWith("ROOM ")) {
            // 群组消息: ROOM <id> <房间> <发送者> <正文>
            String[] t = split3(cmd.substring(5)); // t=[id, 房间, "发送者 正文"]
            if(t == null) return;
            int sp = t[2].indexOf(' ');
            if(sp < 0) return;
            String sender = t[2].substring(0, sp), body = t[2].substring(sp + 1);
            String conv = "#" + t[1];
            boolean mine = isMe(sender);
            boolean mention = !mine && myNick.length() > 0
                && body.toLowerCase().indexOf("@" + myNick.toLowerCase()) >= 0;
            String body2 = mention ? "<b><font color=\"" + Theme.WARN + "\">" + body + "</font></b>" : body;
            ensureConv(conv).putMsg("m" + t[0], mine ? ClientHistory.SELF : ClientHistory.OTHER, sender, body2);
            markUnread(conv);
            notifyIncoming(sender, body, mention);
        } else if(cmd.startsWith("ROOMSYS ")) {
            int sp = cmd.indexOf(' ', 8);
            if(sp < 0) return;
            appendTo("#" + cmd.substring(8, sp), "<font color=\"" + Theme.WARN + "\">" + cmd.substring(sp + 1) + "</font>");
        } else if(cmd.startsWith("READ ")) {
            // 对方已读我发的私聊: 更新气泡为"已读"(功能十九)
            String id = cmd.substring(5).trim();
            String[] cb = (String[])sentPm.get(id);
            if(cb != null) ensureConv(cb[0]).putMsg("m" + id, ClientHistory.SELF, txtNick.getText().trim(),
                    cb[1] + " <font size=\"2\" color=\"" + Theme.OK + "\">· 已读</font>");
        } else if(cmd.startsWith("TYPING ")) {
            showTyping(cmd.substring(7).trim());
        } else if(cmd.startsWith("RECALL ")) {
            // RECALL <id> <撤回者>: 把该消息替换为撤回占位(功能二十)
            int sp = cmd.indexOf(' ', 7);
            String id = (sp < 0) ? cmd.substring(7).trim() : cmd.substring(7, sp);
            String who = (sp < 0) ? "对方" : cmd.substring(sp + 1);
            recallEverywhere("m" + id, who);
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
    // 准入口令(功能十八):服务器要求口令时弹框输入并发 /enter
    private void promptEntryPass() {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            String p = JOptionPane.showInputDialog(ChatClient.this, "该聊天室需要进入口令:", "口令", JOptionPane.QUESTION_MESSAGE);
            if(p != null && ck != null && ck.isConnected()) ck.sendMessage("/enter " + p.trim());
        }});
    }
    // "对方正在输入"提示(功能十九):仅当前会话对象在输入时显示,3 秒后自动清除
    private void showTyping(final String who) {
        if(!who.equals(currentConv)) return;
        typingLabel.setText(" " + who + " 正在输入...");
        if(typingClearTimer != null) typingClearTimer.stop();
        typingClearTimer = new javax.swing.Timer(3000, new ActionListener() {
            public void actionPerformed(ActionEvent ev) { typingLabel.setText(" "); }
        });
        typingClearTimer.setRepeats(false);
        typingClearTimer.start();
    }
    // 撤回(功能二十):同一条消息可能在多个会话窗格出现,逐一替换为撤回占位
    private void recallEverywhere(String key, String who) {
        Iterator it = convs.values().iterator();
        while(it.hasNext()) ((ClientHistory)(it.next())).recall(key, who);
    }
    // ===== 注册 / 登录界面(账号体系 UI 化) =====
    // 弹框填用户名+密码 → 未连接则先连,再发 /register(注册即以该名登录)
    private void doRegister() {
        JTextField u = new JTextField(txtNick.getText().equals(ChatClient.nickText) ? "" : txtNick.getText());
        JPasswordField p = new JPasswordField();
        JPanel form = new JPanel(new GridLayout(0,1,2,2));
        form.add(new JLabel("用户名(至少4个字符):"));
        form.add(u);
        form.add(new JLabel("密码(至少4个字符):"));
        form.add(p);
        int r = JOptionPane.showConfirmDialog(this, form, "注册账号", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if(r != JOptionPane.OK_OPTION) return;
        String name = u.getText().trim();
        String pass = new String(p.getPassword()).trim();
        if(name.length() < 4 || pass.length() < 4) {
            JOptionPane.showMessageDialog(this, "用户名和密码都需至少 4 个字符"); return;
        }
        // 未连接先连接(以默认端口号昵称进,再注册改名)
        if(ck == null || !ck.isConnected()) {
            try {
                ck = new ClientKernel(txtHost.getText(), Integer.parseInt(txtPort.getText()));
                if(!ck.isConnected()) { addMsg("<font color=\"" + Theme.ERR + "\">连接失败</font>"); return; }
                ck.addClient(this);
                myNick = "" + ck.getLocalPort();
                loadOwnerHistory();
            } catch(Exception ex) { addMsg("<font color=\"" + Theme.ERR + "\">连接失败: " + ex.getMessage() + "</font>"); return; }
        }
        txtNick.setText(name);
        lastPassword = pass;              // 缓存,断线重连自动验证
        ck.sendMessage("/register " + name + " " + pass);
    }
    // 输入已注册昵称连接后,服务器要求验证时:自动弹密码框(账号"登录"体验)
    private void promptVerify(final String nick) {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            JPasswordField p = new JPasswordField();
            int r = JOptionPane.showConfirmDialog(ChatClient.this, p,
                "昵称 " + nick + " 已注册,请输入密码登录", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if(r == JOptionPane.OK_OPTION && ck != null && ck.isConnected()) {
                String pass = new String(p.getPassword()).trim();
                lastPassword = pass;
                ck.sendMessage("/verify " + pass);
            }
        }});
    }
    // 加入/创建群组(功能十七)
    private void joinRoom() {
        if(ck == null || !ck.isConnected()) { addMsg("<font color=\"" + Theme.ERR + "\">请先连接服务器</font>"); return; }
        String r = JOptionPane.showInputDialog(this, "输入群组名(不存在则创建):", "加入/创建群组", JOptionPane.QUESTION_MESSAGE);
        if(r == null) return;
        r = r.trim().replace(' ', '_');
        if(r.length() > 0) { ck.sendMessage("/join " + r); openConv("#" + r); }
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
        } else if(lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".m4a")) {
            return "<font color=\"" + Theme.PRIV + "\">[语音] " + fname
                    + "（<a href=\"" + uri + "\">点击播放</a>）</font>"; // 功能二十三
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
            h.convName = name;
            convs.put(name, h);
            centerCards.add(new JScrollPane(h), name);
            // 非好友、且不是群组(#开头)的会话才算"临时会话"
            if(!name.startsWith("#") && !containsIgnoreCase(friends, name)) tempConvs.add(name);
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
            peerTitle.setText("在线用户");
            eastLayout.show(eastCards, "users"); // 公共聊天室:右栏显示全部在线用户
        } else if(name.startsWith("#")) {
            peerTitle.setText("群成员");
            eastLayout.show(eastCards, "info");  // 群组:右栏显示本群成员
            buttonAddFriend.setVisible(false);
            infoPane.setText("加载群成员...");
            if(ck != null && ck.isConnected()) ck.sendMessage("/roommem " + name.substring(1));
        } else {
            peerTitle.setText("对方信息");
            eastLayout.show(eastCards, "info");
            buttonAddFriend.setVisible(!containsIgnoreCase(friends, name)); // 已是好友就不显示"加为好友"
            infoPane.setText("查询中...");
            if(ck != null && ck.isConnected()) {
                ck.sendMessage("/infoq " + name); // 静默拉取对方信息填右栏
                Object rid = lastRecvId.get(name);
                if(rid != null) ck.sendMessage("/read " + rid); // 打开私聊会话即回执已读(功能十九)
            }
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
        if(rooms.size() > 0) {
            addConvItem("── 群组 ──", null);
            for(int i=0;i<rooms.size();i++) addConvItem("# " + rooms.get(i), "#" + rooms.get(i));
        }
        boolean headerAdded = false;
        for(int i=0;i<tempConvs.size();i++) {
            String t = (String)tempConvs.get(i);
            if(t.startsWith("#") || containsIgnoreCase(friends, t)) continue; // 群组、已加好友的不算"临时"
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
    // 是否是我发的消息:优先用服务器确认的 myNick,退化到昵称框(兼容极早期未确认的一瞬)
    private boolean isMe(String nick) {
        if(myNick.length() > 0 && nick.equalsIgnoreCase(myNick)) return true;
        return nick.equalsIgnoreCase(txtNick.getText().trim());
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
        JMenuItem shake = new JMenuItem("发送窗口抖动");
        shake.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                if(ck != null && ck.isConnected()) ck.sendMessage("/shake " + name);
            }
        });
        menu.add(shake);
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
        convList.setSelectedIndex(idx);
        JPopupMenu menu = new JPopupMenu();
        if(name.startsWith("#")) {                 // 群组:退出群组
            final String room = name.substring(1);
            JMenuItem leave = new JMenuItem("退出群组");
            leave.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    if(ck != null && ck.isConnected()) ck.sendMessage("/part " + room);
                }
            });
            menu.add(leave);
        } else if(containsIgnoreCase(friends, name)) { // 好友:删除好友
            JMenuItem del = new JMenuItem("删除好友");
            del.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    if(ck != null && ck.isConnected()) ck.sendMessage("/delfriend " + name);
                }
            });
            menu.add(del);
        }
        // 所有会话都可清空本地聊天记录(功能二十一)
        JMenuItem clr = new JMenuItem("清空聊天记录");
        clr.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) { clearHistory(name); }
        });
        menu.add(clr);
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
            String nk = str.substring(i + marker.length()).trim();
            txtNick.setText(nk);
            myNick = nk; // 服务器确认的权威昵称,用于消息归属判断
            loadOwnerHistory(); // 身份确立/切换 → 载入该身份的历史(同身份自动跳过)
            // 昵称确立即拉取自己的信息回填顶部编辑区
            if(ck != null && ck.isConnected()) ck.sendMessage("/infoq " + nk);
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
            // 记录我的昵称:填了就是它,没填则是服务器默认的端口号昵称
            myNick = nickSet ? txtNick.getText().trim() : "" + ck.getLocalPort();
            if(ck.isConnected()) {
                ck.addClient(this);
                loadOwnerHistory(); // 载入"我这个身份"的历史(先于本次会话提示)
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
                        boolean has = n.length() > 0 && !n.equals(ChatClient.nickText);
                        if(has) ck.setNick(n); // 取回原昵称
                        myNick = has ? n : "" + ck.getLocalPort();
                        loadOwnerHistory(); // 同身份则跳过(不重复加载)
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
        // 会话感知:命令原样发;群组会话→/room;私聊会话→/msg;公共聊天室→原样广播。
        // 句首为表情短码(/微笑 等)不算命令,否则表情放句首会被当未知命令吞掉
        boolean isCmd = toSend.startsWith("/") && !startsWithEmote(toSend);
        String wire;
        if(isCmd) wire = toSend;
        else if(currentConv.startsWith("#")) wire = "/room " + currentConv.substring(1) + " " + toSend;
        else if(!MAIN_ROOM.equals(currentConv)) wire = "/msg " + currentConv + " " + toSend;
        else wire = toSend.startsWith("/") ? " " + toSend : toSend; // 前置空格防内核把表情当命令
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
        // 截图直接粘贴(功能二十五):输入框 Ctrl+V,剪贴板里是图片就直接作为图片消息发出
        if(e.getSource() == msgWindow && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
            try {
                java.awt.datatransfer.Transferable t =
                    Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if(t != null && t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
                    e.consume(); // 拦下默认粘贴(避免贴出乱码文本)
                    if(ck == null || !ck.isConnected()) { addMsg("<font color=\"" + Theme.ERR + "\">请先连接服务器</font>"); return; }
                    Image img = (Image)t.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);
                    java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(
                        img.getWidth(null), img.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = bi.createGraphics();
                    g.drawImage(img, 0, 0, null);
                    g.dispose();
                    java.io.File f = new java.io.File(System.getProperty("java.io.tmpdir"),
                        "paste_" + System.currentTimeMillis() + ".png");
                    javax.imageio.ImageIO.write(bi, "png", f);
                    sendFileChunked(MAIN_ROOM.equals(currentConv) ? "*" : currentConv, f);
                }
            } catch(Exception ex) {}
        }
        if(e.getKeyCode() == KeyEvent.VK_ESCAPE) setState(JFrame.ICONIFIED); // Esc 最小化(到托盘)
    }
    public void keyReleased(KeyEvent e) {
        if(e.getSource() == msgWindow) {
            if(e.getKeyCode() == KeyEvent.VK_UP) msgWindow.setText(lastMsg);
            else sendTyping(); // 私聊会话中打字 → 节流通知对方"正在输入"(功能十九)
        }
    }
    private void sendTyping() {
        if(ck == null || !ck.isConnected()) return;
        if(MAIN_ROOM.equals(currentConv) || currentConv.startsWith("#")) return; // 仅私聊
        long now = System.currentTimeMillis();
        if(now - lastTypingSent < 2000) return; // 2 秒节流,防刷屏
        lastTypingSent = now;
        ck.sendMessage("/typing " + currentConv);
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
        if(e.getSource()==buttonRoom) joinRoom();
        if(e.getSource()==buttonTheme) { Theme.toggle(); applyTheme(); }
        if(e.getSource()==buttonAddRow) infoModel.addRow(new Object[]{"",""});
        if(e.getSource()==buttonSaveInfo) saveMyInfo();
        if(e.getSource()==buttonAvatar) chooseAvatar();
        if(e.getSource()==buttonEmote) showEmoteMenu();
        if(e.getSource()==buttonRegister) doRegister();
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
        class Msg { int type; String sender, body, time; long rid = -1; } // rid=可撤回的消息 id
        private Vector msgs = new Vector();
        private HashMap keyIdx = new HashMap(); // 消息键 -> 下标
        String convName;             // 所属会话名(本地记录落盘用,功能二十一)
        private boolean loading = false; // 回填历史时不重复落盘
        public ClientHistory() {
            super("text/html", "");
            setEditable(false);
            setAutoscrolls(true);
            addHyperlinkListener(new HyperlinkListener() {
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if(e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
                    String href = e.getDescription();
                    if(href != null && href.startsWith("recall:")) { // 撤回链接(功能二十)
                        if(ck != null && ck.isConnected()) ck.sendMessage("/recall " + href.substring(7));
                        return;
                    }
                    try { Desktop.getDesktop().open(new java.io.File(e.getURL().toURI())); } // 原图/播放/打开
                    catch(Exception ex) { System.out.println("open link: " + ex.getMessage()); }
                }
            });
            renderAll();
        }
        private String now() { return new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()); }
        public void addSystem(String body) { add(null, SYS, null, body); }
        // 带键追加/原位更新:同键第二次调用替换原消息(文件进度、撤回/回执)
        public void putMsg(String key, int type, String sender, String body) { add(key, type, sender, body); }
        private void add(String key, int type, String sender, String body) {
            Integer i = (key == null) ? null : (Integer)keyIdx.get(key);
            Msg m;
            if(i == null) {
                m = new Msg();
                m.time = now();
                if(key != null && key.startsWith("m")) { // "m<id>" 是真实聊天消息,可撤回
                    try { m.rid = Long.parseLong(key.substring(1)); } catch(Exception e) {}
                }
                if(key != null) keyIdx.put(key, Integer.valueOf(msgs.size()));
                msgs.add(m);
            } else {
                m = (Msg)msgs.get(i.intValue());
            }
            m.type = type; m.sender = sender; m.body = body;
            renderAll();
            if(!loading) saveHistory(convName, this); // 落盘(功能二十一)
        }
        // 撤回:把带该键的消息替换为居中的撤回占位(功能二十)
        public void recall(String key, String who) {
            Integer i = (Integer)keyIdx.get(key);
            if(i == null) return;
            Msg m = (Msg)msgs.get(i.intValue());
            m.type = SYS; m.rid = -1;
            m.body = who + " 撤回了一条消息";
            renderAll();
            saveHistory(convName, this);
        }
        // 序列化非系统消息(每行:类型发送者时间rid正文)
        String serialize() {
            StringBuffer sb = new StringBuffer();
            for(int i=0;i<msgs.size();i++) {
                Msg m = (Msg)msgs.get(i);
                if(m.type == SYS) continue; // 系统提示/撤回占位不入历史
                if(sb.length() > 0) sb.append('\n');
                sb.append(m.type).append('').append(m.sender == null ? "" : m.sender)
                  .append('').append(m.time).append('').append(m.rid)
                  .append('').append(m.body.replace('\n',' ').replace('\r',' '));
            }
            return sb.toString();
        }
        // 从历史行回填一条(不重新计时、不落盘)
        void addLoaded(String line) {
            String[] p = line.split("", 5);
            if(p.length < 5) return;
            Msg m = new Msg();
            try { m.type = Integer.parseInt(p[0]); } catch(Exception e) { return; }
            m.sender = p[1].length() == 0 ? null : p[1];
            m.time = p[2];
            try { m.rid = Long.parseLong(p[3]); } catch(Exception e) {}
            m.body = p[4];
            m.rid = -1; // 历史消息:清掉可寻址 id —— 服务器重启后消息 ID 会从头重排,
            //            若保留旧 id 作键,新会话首条消息(同 id)会误覆盖这条历史。
            //            历史消息本就不可再撤回/更新,不登记键即可,只作只读展示。
            msgs.add(m);
        }
        private String renderMsg(Msg m) {
            if(m.type == SYS) // 系统消息:居中、小字、弱化
                return "<table width=\"100%\" cellpadding=\"2\"><tr><td align=\"center\">"
                     + "<font color=\"" + Theme.TIME + "\" size=\"2\">" + m.body + "</font></td></tr></table>";
            boolean self = m.type == SELF;
            String align = self ? "right" : "left";
            String bub = self ? Theme.SELFBUB : Theme.OTHERBUB;
            // 自己的消息附一个"撤回"小链接(限时由服务器校验)
            String recall = (self && m.rid > 0) ? "&nbsp;<font size=\"2\"><a href=\"recall:" + m.rid + "\">撤回</a></font>" : "";
            String head = self ? "<font color=\"" + Theme.TIME + "\" size=\"2\">" + m.time + recall + "</font>"
                               : "<font color=\"" + Theme.TIME + "\" size=\"2\">" + m.sender + "&nbsp;&nbsp;" + m.time + "</font>";
            // 气泡旁头像(仿微信):对方在左、自己在右;无头像用默认灰色人形。源图 128px 缩到 36px 显示,清晰
            String av = "<img src=\"" + avatarFileOf(m.sender).toURI() + "\" width=\"36\" height=\"36\">";
            String msgCell = "<td align=\"" + align + "\">" + head + "<br>"
                 + "<table bgcolor=\"" + bub + "\" cellpadding=\"6\" cellspacing=\"0\"><tr><td>"
                 + "<font color=\"" + Theme.BUBTX + "\">" + Emotes.apply(m.body) + "</font>" // 短码→表情图(功能二十六)
                 + "</td></tr></table></td>";
            String avCell = "<td width=\"34\" valign=\"top\">" + av + "</td>";
            return "<table width=\"100%\" cellpadding=\"3\"><tr>"
                 + (self ? msgCell + avCell : avCell + msgCell)
                 + "</tr></table>";
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

