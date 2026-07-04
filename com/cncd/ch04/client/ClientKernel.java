package com.cncd.ch04.client;
import java.io.*;
import java.net.*;
import java.util.*;
public class ClientKernel {
    public static final char MSGENDCHAR = 0xff;
    public static final char EXIT = 0xFE;
    public static final char NICK = 0xFD;
    public static final char COMMAND = 0xFD;
    public static final char PUSHMARKER = 0x01; // 服务器主动推送（在线名单等）的标记，与服务器端约定一致
    
    private String serverAd;
    private int port;
    private Socket sock;
    private boolean isConnected = false;
    private boolean dropMe = false;
    private LinkedList clients;
    public String nick;
    public boolean printMsg = true;
    private ClientMsgSender cms;
    private ClientMsgListener cml;
    /** Creates a new instance of ClientKernel */
    public ClientKernel(String server, int port) {
        this.port = port;
        nick = "" + port;
        serverAd = server;
        clients = new LinkedList();
        connect();
        if(isConnected) {
            cms = new ClientMsgSender(this, sock);
            cml = new ClientMsgListener(this, sock);
        }
    }
    public void connect() {
        try {
            sock = new Socket(serverAd, port);
            isConnected = true;
        } catch(IOException ioe ) {
            ioe.printStackTrace();
        }
    }
    public int getPort() {
        return port;
    }
    public boolean setNick(String nick) {
        sendMessage("" + ClientKernel.COMMAND + "nick " + nick);
        return true;
    }
    public int getLocalPort() {
        return sock.getLocalPort();
    }
    public void dropMe() {
        dropMe = true;
        isConnected = false;
        if(cms != null) cms.drop();
        if(cml != null) cml.drop();
        // 关闭 socket，迫使阻塞在 read() 上的接收线程退出
        // （原代码不关闭，旧连接残留导致重连后每条消息收两遍）
        try { if(sock != null) sock.close(); } catch(Exception e) {}
        // 原代码等待条件写反（"都停了"才进循环）；连接失败时 cms/cml 为 null 也要防
        while(cms != null && cml != null && !(cms.hasStoped() && cml.hasStoped())) pause(5);
    }
    public void sendMessage(String str) {
        if(str == null || str.length() == 0) return; // 防止发送空消息时 charAt(0) 越界
        if(!dropMe) {
            if(str.charAt(0) == '/')
                cms.addMessage("" + ClientKernel.COMMAND + str.substring(1) );
            else cms.addMessage(str);
        }
    }
    // 发送文件：Base64 编码后作为 file 命令走既有消息通道
    // （Base64 为纯 ASCII，不会与 0xFF/0xFD/0x01 协议字节冲突）
    public void sendFile(String target, String fname, String b64) {
        if(!dropMe)
            cms.addMessage("" + ClientKernel.COMMAND + "file " + target + " " + fname + " " + b64);
    }
    public void addClient(ChatClient c) {
        clients.add(c);
    }
    public void removeClient(ChatClient c) {
        clients.remove(c);
    }
    public void pause(int time) {
        try {
            Thread.sleep(time);
        } catch(Exception e) {}
    }
    public synchronized void storeMsg(String str) {
        Object[] client = clients.toArray();
        for(int i=0;i<client.length;i++)
            ((ChatClient)(client[i])).addMsg(str);
    }
    public boolean isConnected() {
        return isConnected;
    }
    // 接收线程检测到连接意外断开时回调：复位连接标志并提示用户。
    // 原程序 isConnected 置 true 后永不复位，服务器重启后消息会静默写进死连接
    public void connectionLost() {
        if(!isConnected) return;
        isConnected = false;
        storeMsg("" + PUSHMARKER + "USERLIST"); // 空名单 → 界面清空在线用户列表
        storeMsg("<font color=\"#ff0000\">与服务器的连接已断开，请重新 Connect</font>");
    }
    public static void main(String args[]) {
        new ClientKernel("localhost", 1984);
    }
}
class ClientMsgSender extends Thread {
    private Socket s;
    private ClientKernel ck;
    private LinkedList msgList;
    private boolean running = true;
    private boolean hasStoped = false;
    public ClientMsgSender(ClientKernel ck, Socket s) {
        this.ck = ck;
        this.s  = s;
        msgList = new LinkedList();
        start();
    }
    public synchronized void addMessage(String msg) {
        msgList.addLast(msg);
    }
    public void drop() {
        running = false;
    }
    public boolean hasStoped() {
        return hasStoped;
    }
    public void run() {
        try {
            DataOutputStream dataOut = new DataOutputStream(s.getOutputStream());
            while(running) {
                while(msgList.size()>0) {
                    String msg = ((String)(msgList.removeFirst()));
                    // 中文修复：不再逐字符写低8位（会把汉字截断），而是整条消息按 UTF-8 编码成字节发送。
                    // 命令消息以控制字节 0xFD(COMMAND) 开头，该标记单独作为原始字节写出，
                    // 其余正文才做 UTF-8 编码，避免把 0xFD 也编码成两字节而认不出来。
                    if(msg.length()>0 && msg.charAt(0) == ClientKernel.COMMAND) {
                        dataOut.write(ClientKernel.COMMAND);
                        dataOut.write(msg.substring(1).getBytes("UTF-8"));
                    } else {
                        dataOut.write(msg.getBytes("UTF-8"));
                    }
                    dataOut.write(ClientKernel.MSGENDCHAR);
                }
                sleep(10);
            }
            // Thread.stop() 在 JDK17 已被禁用（抛异常），循环退出后线程自然结束即可
            try { dataOut.write(ClientKernel.EXIT); dataOut.close(); } catch(Exception e) {}
        } catch(Exception ioe) {
            if(running) ioe.printStackTrace(); // 主动断开时 socket 已关闭，异常属预期
        } finally {
            hasStoped = true;
        }
    }
}
class ClientMsgListener extends Thread{
    private ClientKernel ck;
    private Socket s;
    private boolean running = true;
    private boolean hasStoped = false;
    public ClientMsgListener(ClientKernel ck, Socket s) {
        this.ck = ck;
        this.s  = s;
        start();
    }
    public void drop() {
        running = false;
    }
    public boolean hasStoped() {
        return hasStoped;
    }
    public void run() {
        try {
                BufferedInputStream buffIn = new BufferedInputStream(s.getInputStream());
                DataInputStream dataIn = new DataInputStream(buffIn);
                while(running) {
                    // 中文修复 + 健壮性：把一条消息的字节先收进缓冲区，遇到结束符 0xFF 或
                    // 连接断开(-1)才停止，然后整体按 UTF-8 解码；避免逐字节转 char 造成乱码，
                    // 同时修掉原代码 read() 返回 -1 后无限 append 的死循环。
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    int c;
                    while( (c=dataIn.read()) != ClientKernel.MSGENDCHAR && c != -1) {
                        baos.write(c);
                    }
                    if(c == -1) break;
                    ck.storeMsg(new String(baos.toByteArray(), "UTF-8"));
                }
                dataIn.close();
                buffIn.close();
        } catch(IOException ioe) {
            if(running) ioe.printStackTrace(); // 主动断开时 socket 已关闭，异常属预期
        } finally {
            hasStoped = true;
            // running 仍为 true 说明不是 drop() 主动断开，而是服务器侧断线 → 通知内核
            if(running) ck.connectionLost();
        }
    }
}
