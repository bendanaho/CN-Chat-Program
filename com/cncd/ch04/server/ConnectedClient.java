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
    public ConnectedClient(Socket sock, ConnectionKeeper ck) {
        this.ck = ck;
        ipNumber = sock.getInetAddress().getHostAddress();
        portNumber = sock.getPort();
        this.sock = sock;
        msgSend = new ServerMsgSender(this.sock, this);
        msgList = new ServerMsgListener(this.sock, this);
        nick = "" + portNumber;
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
                    // 中文修复：writeBytes 只写每个 char 的低 8 位，会截断汉字；
                    // 改成先按 UTF-8 编码正文，再单独写 1 字节结束符 0xFF。
                    dataOut.write(toSend.getBytes("UTF-8"));
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
                // 中文修复：先把一条消息的原始字节收进缓冲区，遇到结束符 0xFF 或断开(-1)才停止，
                // 再整体 UTF-8 解码。命令消息以控制字节 0xFD 开头，这里单独识别，不把它放进
                // UTF-8 正文（0xFD 不是合法 UTF-8 字节，混进去会破坏解码）。
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                boolean didRun = false;
                boolean isCommand = false;
                int c;
                sleep(10);
                c = dataIn.read();
                if(c == 0xFD) { isCommand = true; didRun = true; c = dataIn.read(); }
                while(c != 0xff && c != -1) {
                    baos.write(c);
                    didRun = true;
                    c = dataIn.read();
                }
                if(c == -1) { cc.dropClient(); return; } // 对端断开，干净退出（原代码会死循环）
                String body = new String(baos.toByteArray(), "UTF-8");
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
                if(didRun) {
                    if(!isCommand) {
                        String toSend = "" + cc.nick + ":" + body;
                        if(cc.printMsg) System.out.println("MsgListenet.run Sending msg: " + toSend);
                        cc.broadcastMessage(toSend);
                    } else {
                        // 还原成 "0xFD + 正文" 交给 runCommand，保持它 charAt(0)==0xFD 再 substring(1) 的老约定不变
                        cc.runCommand("" + (char)0xFD + body);
                    }
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

