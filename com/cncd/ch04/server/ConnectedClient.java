package com.cncd.ch04.server;
import java.io.*;
import java.net.*;
import java.util.*;
public class ConnectedClient {
    private ConnectionKeeper ck;
    public String nick;
    public Date connectedTime;
    public String ipNumber;
    public int portNumber;
    public boolean verifyedBoolean = false;
    public int verifyedCount = 0;
    public String tmpNick = "";
    private ServerMsgSender msgSend;
    private ServerMsgListener msgList;
    private Socket sock;
    public boolean printMsg = false;
    public volatile long lastHeard = System.currentTimeMillis(); // 最近一次收到该客户端消息的时刻(心跳清扫用)
    public boolean entered = MainServer.ENTRYPASS.length() == 0;  // 准入(功能十八):无口令则默认已准入
    public ConnectedClient(Socket sock, ConnectionKeeper ck) {
        this.ck = ck;
        ipNumber = sock.getInetAddress().getHostAddress();
        portNumber = sock.getPort();
        this.sock = sock;
        msgSend = new ServerMsgSender(this.sock, this);
        msgList = new ServerMsgListener(this.sock, this);
        nick = "" + portNumber;
        if(!entered) sendMessage("" + MainServer.PUSHMARKER + "NEEDPASS"); // 提示客户端弹口令框
    }
    public ConnectionKeeper getConnectionKeeper() {
            return ck;
    }
    public String getNick() {
            return nick;
    }
    public void sendMessage(String str) {
        msgSend.addMessage(str);
    }
    // 被禁言者尝试发言时的节流提示(功能十八):同一状态 5 秒内只提示一次,避免连发刷屏
    private long lastMuteNotice = 0;
    public void muteNotice() {
        long now = System.currentTimeMillis();
        if(now - lastMuteNotice > 5000) {
            lastMuteNotice = now;
            sendMessage("<font color=\"#ff8800\">你处于禁言状态，消息未发送</font>");
        }
    }
    public boolean sendTo(String user, String msg) {
        return ck.sendTo(this, user, msg);
    }
    public void broadcastMessage(String str) {
        if(!isSpam(str)) ck.broadcast(str);
    }
    public void dropClient() {
        msgList.closeConnection();
        msgSend.closeConnection();
        try { sock.close(); } catch(Exception e) {} // 解除接收线程的 read() 阻塞，连接真正释放
        MainServer.rooms.leaveAll(nick); // 下线时退出所有群组(功能十七)
        ck.remove(this);
    }
    public void runCommand(String str) {
        if(str.charAt(0)==0xFD) {
            String str1 = str.substring(1);
            ck.runCommand(this, str1);
        }
    }
    private boolean isSpam(String str) {
        return false;
    }
    public static void main(String arg[]) {
        MainServer ms = new MainServer(1984);
    }
    public void whoAmI() {
        String str = "<br>Connected Port: " + portNumber + "<br>" +
                     "Nick: " + nick + "<br>";
        sendMessage(str);
    }
}
class ServerMsgSender extends Thread {
    private Socket sock;
    private LinkedList msgList;
    private ConnectedClient cc;
    private boolean running = true;
    public ServerMsgSender(Socket sock, ConnectedClient cc) {
        this.sock = sock;
        this.cc = cc;
        collectInfo();
        msgList = new LinkedList();
        start();
    }
    public synchronized void addMessage(String str) {
        if(cc.printMsg) System.out.println("MsgSender.addMessage: " +str);
        msgList.addLast(str);
    }
    private void collectInfo() {
    }
    public void run() {
        try {
            DataOutputStream dataOut = new DataOutputStream(sock.getOutputStream());
            while(running) {
                while(msgList.size()>0) {
                    String toSend = (String)(msgList.removeFirst());
                    // UTF-8 编码 → 加密层包裹(AES+Base64,关闭时原样) → 0xFF 分帧
                    dataOut.write(com.cncd.ch04.common.CryptoUtil.wrap(toSend.getBytes("UTF-8")));
                    dataOut.write(MainServer.MSGENDCHAR);
                    if(cc.printMsg) System.out.println("MsgSender.run: Sending: " + toSend);
                    sleep(10);
                }
                sleep(10);
            }
        } catch(Exception e) {
            // 断开判断改按异常类型。原代码用 e.getMessage() 文本匹配：文本随 JDK/系统语言
            // 变化，且 getMessage() 可能为 null（在 catch 块内再抛 NPE，线程带着连接静默死亡）
            if(e instanceof SocketException || e instanceof EOFException) {
                System.out.println("MsgSender.run Client disconnected nick: " + cc.nick);
            } else {
                e.printStackTrace();
            }
            cc.dropClient();
        }
    }
    public void closeConnection() {
        running = false;
    }
}
class ServerMsgListener extends Thread {
    private LinkedList msgList;
    private Socket sock;
    private ConnectedClient cc;
    private boolean running = true;
    public ServerMsgListener(Socket s, ConnectedClient cc) {
        msgList = new LinkedList();
        sock = s;
        this.cc = cc;
        start();
    }
    public void closeConnection() {
        running = false;
    }
    public void run() {
        try {
            BufferedInputStream buffIn = new BufferedInputStream(sock.getInputStream());
            DataInputStream dataIn = new DataInputStream(buffIn);
            while(running) {
                // 把一帧的全部字节收进缓冲区(0xFF 分帧/-1 断开),先过解密层还原明文字节,
                // 再看首字节是否 0xFD 命令标记,其余按 UTF-8 解码。
                // (加密后 0xFD 在密文内部,必须先整帧解密再判别——这是接入加密层时的关键顺序)
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                boolean didRun = false;
                int c;
                sleep(10);
                while( (c = dataIn.read()) != 0xff && c != -1) {
                    baos.write(c);// 攒到 0xFF
                    didRun = true;
                }
                if(c == -1) { cc.dropClient(); return; } // 对端断开，干净退出（原代码会死循环）
                if(!didRun) continue;
                byte[] raw = com.cncd.ch04.common.CryptoUtil.unwrap(baos.toByteArray());
                boolean isCommand = raw.length > 0 && (raw[0] & 0xFF) == 0xFD;
                String body = isCommand ? new String(raw, 1, raw.length - 1, "UTF-8")
                                        : new String(raw, "UTF-8");
                cc.lastHeard = System.currentTimeMillis(); // 收到任何消息都算"活着"
                if(cc.verifyedCount>0 && !cc.verifyedBoolean && !isCommand) {
                    cc.verifyedCount--;
                    if(cc.verifyedCount==1) {
                        cc.sendMessage("You have failed to verify your nick");
                        cc.nick = "" + cc.portNumber;
                        cc.sendMessage("Your nick is " + cc.nick);
                        cc.getConnectionKeeper().broadcastUserList(); // 昵称被重置，同步名单
                    } else {
                        cc.sendMessage("type: \"/verify &lt;password&gt\" to verify your nick");
                    }
                }
                if(!isCommand) {
                    if(body.trim().length() == 0) continue; // 空消息不广播
                    if(!cc.entered) { cc.sendMessage("Server: 需要进入口令,请输入 /enter <口令>"); continue; }
                    // 群聊消息带唯一 ID:0x01 MSG <id> <昵称> <正文>;记录元数据供回执/撤回
                    long id = MainServer.nextMsgId();
                    MainServer.recordMsg(id, cc.nick, "*");
                    String toSend = "" + MainServer.PUSHMARKER + "MSG " + id + " " + cc.nick + " " + body;
                    if(cc.printMsg) System.out.println("MsgListenet.run Sending msg: " + toSend);
                    if(MainServer.isMuted(cc.nick)) cc.muteNotice(); // 被禁言:直接丢弃,不回显假气泡,只提示未发送(功能十八)
                    else cc.broadcastMessage(toSend);
                } else {
                    // 准入门禁:未通过口令时只放行 enter 命令(功能十八)
                    if(!cc.entered && !body.toLowerCase().startsWith("enter")) {
                        cc.sendMessage("Server: 需要进入口令,请输入 /enter <口令>"); continue;
                    }
                    // 还原成 "0xFD + 正文" 交给 runCommand，保持它 charAt(0)==0xFD 再 substring(1) 的老约定不变
                    cc.runCommand("" + (char)0xFD + body);
                }
            }
        } catch(SocketException se) {
            cc.dropClient(); // 连接类异常一律清理，不再依赖报错文本（getMessage 可能为 null）
        } catch(Exception e) {
            e.printStackTrace();
            cc.dropClient();
        }
    }
}

