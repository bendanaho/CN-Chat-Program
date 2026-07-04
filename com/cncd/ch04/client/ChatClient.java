package com.cncd.ch04.client;
import javax.swing.*;
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
        //创建Center
        historyWindow = new ClientHistory();
        sc = new JScrollPane(historyWindow);
        sc.setAutoscrolls(true);
        this.add(sc, BorderLayout.CENTER);
        //创建East：在线用户列表（服务器推送，实时刷新）
        userModel = new DefaultListModel();
        userList = new JList(userModel);
        // 双击在线用户名 → 输入框自动填好私聊命令前缀，光标就位直接打内容
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                // 点列表空白处取消选中（选中=文件私发对象，取消=群发）
                int idx = userList.locationToIndex(e.getPoint());
                if(idx >= 0 && !userList.getCellBounds(idx, idx).contains(e.getPoint())) {
                    userList.clearSelection();
                    return;
                }
                if(e.getClickCount() == 2) {
                    Object v = userList.getSelectedValue();
                    if(v != null) {
                        if(v.toString().equalsIgnoreCase(txtNick.getText())) return; // 自己不预填(服务器端也会拦)
                        msgWindow.setText("/msg " + v + " ");
                        msgWindow.requestFocus();
                    }
                }
            }
        });
        JPanel eastPanel = new JPanel(new BorderLayout());
        eastPanel.add(new JLabel("在线用户", JLabel.CENTER), BorderLayout.NORTH);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(110, 0));
        eastPanel.add(userScroll, BorderLayout.CENTER);
        this.add(eastPanel, BorderLayout.EAST);
    }
   public static void main(String args[]) {
        ChatClient client = new ChatClient();
        client.setTitle(client.appName);
        client.setSize(450, 500);
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
        historyWindow.addText(str);
        syncNick(str);
    }
    // 处理服务器主动推送："USERLIST 名字..." 在线名单 / "FILE 发送者 文件名 Base64" 文件
    private void handlePush(String cmd) {
        if(cmd.startsWith("USERLIST")) {
            userModel.clear();
            StringTokenizer st = new StringTokenizer(cmd.substring(8));
            while(st.hasMoreTokens()) userModel.addElement(st.nextToken());
        } else if(cmd.startsWith("FILE ")) {
            receiveFile(cmd.substring(5));
        }
    }
    // 接收文件：解码 Base64 存到 用户主目录\ChatDownloads\，图片直接内嵌显示在聊天区
    private void receiveFile(String rest) {
        try {
            int p1 = rest.indexOf(' ');
            int p2 = rest.indexOf(' ', p1 + 1);
            if(p1 < 0 || p2 < 0) return;
            String sender = rest.substring(0, p1);
            // 文件名来自网络，剥掉路径分隔符防止目录穿越（如 ..\evil.exe 写到别处）
            String fname = rest.substring(p1 + 1, p2).replace('\\', '_').replace('/', '_');
            byte[] data = Base64.getDecoder().decode(rest.substring(p2 + 1));
            java.io.File dir = new java.io.File(System.getProperty("user.home"), "ChatDownloads");
            dir.mkdirs();
            java.io.File out = new java.io.File(dir, fname);
            if(out.exists()) out = new java.io.File(dir, System.currentTimeMillis() + "_" + fname);
            java.nio.file.Files.write(out.toPath(), data);
            String lower = fname.toLowerCase();
            if(lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
                // 聊天区是 HTML 渲染，图片用 <img> 内嵌显示（文档要求⑥）
                addMsg("<font color=\"#9933cc\">[图片] 来自 " + sender + ": " + fname
                        + "</font><br><img src=\"" + out.toURI() + "\">");
            } else {
                addMsg("<font color=\"#9933cc\">[文件] 来自 " + sender + ": " + fname
                        + "（已保存到 " + out.getAbsolutePath() + "）</font>");
            }
        } catch(Exception ex) {
            addMsg("<font color=\"#ff0000\">接收文件失败: " + ex.getMessage() + "</font>");
        }
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
    }
    // 发送文件/图片：右侧选中了人=私发给他，没选人=群发给所有人
    private void sendFile() {
        if(ck == null || !ck.isConnected()) {
            addMsg("<font color=\"#ff0000\">尚未连接服务器，请先点击 Connect</font>");
            return;
        }
        Object sel = userList.getSelectedValue();
        String target = (sel == null) ? "*" : sel.toString();
        if(sel != null && target.equalsIgnoreCase(txtNick.getText())) {
            addMsg("不能给自己发文件，请选择其他用户，或取消选择进行群发");
            return;
        }
        JFileChooser fc = new JFileChooser();
        if(fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        if(f.length() > 5*1024*1024) {
            addMsg("<font color=\"#ff0000\">文件过大（限 5MB）：" + f.getName() + "</font>");
            return;
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            String b64 = Base64.getEncoder().encodeToString(data);
            String fname = f.getName().replace(' ', '_'); // 协议按空格分隔字段，文件名里的空格转下划线
            ck.sendFile(target, fname, b64);
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

