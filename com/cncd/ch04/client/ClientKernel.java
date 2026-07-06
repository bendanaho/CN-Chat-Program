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
            new Heartbeat().start(); // 心跳保活(功能十一)
        }
    }
    // ===== 心跳(功能十一):定时发 /hb,服务器原样回显;既证明自己活着,也检测服务器死没死 =====
    private volatile long lastHbReply = System.currentTimeMillis();
    private int hbSeq = 0;
    public volatile long lastRtt = -1; // 最近一次心跳往返时延(功能十五网络面板数据源)
    // 网络统计(功能十五)
    public volatile long rttMin = Long.MAX_VALUE, rttMax = -1, rttSum = 0, rttCount = 0;
    public volatile int hbSent = 0, hbRecv = 0;              // 心跳发/收计数 → 丢包率
    public volatile long bytesOut = 0, bytesIn = 0;         // 收发字节累计
    public final long connectStart = System.currentTimeMillis();
    public void addBytesOut(long n) { bytesOut += n; }
    public void addBytesIn(long n) { bytesIn += n; }
    private String kb(long b) { return b < 1024 ? b + "B" : (b/1024) + "KB"; }
    private String upfmt(long s) { return (s/60) + ":" + (s%60 < 10 ? "0" : "") + (s%60); }
    // 组装状态栏文本:RTT 四项 + 丢包率 + 收发流量 + 在线时长
    public String statsLine() {
        long up = (System.currentTimeMillis() - connectStart) / 1000;
        String cur = lastRtt < 0 ? "--" : lastRtt + "ms";
        String avg = rttCount == 0 ? "--" : (rttSum / rttCount) + "ms";
        String mn = rttMin == Long.MAX_VALUE ? "--" : rttMin + "";
        String mx = rttMax < 0 ? "--" : rttMax + "";
        int loss = hbSent == 0 ? 0 : (int)((hbSent - hbRecv) * 100L / hbSent);
        return "  RTT " + cur + " (最小 " + mn + " / 平均 " + avg + " / 最大 " + mx + "ms)"
             + "　丢包 " + loss + "%　收 " + kb(bytesIn) + " / 发 " + kb(bytesOut)
             + "　在线 " + upfmt(up);
    }
    class Heartbeat extends Thread {
        public Heartbeat() { setDaemon(true); }
        public void run() {
            while(!dropMe && isConnected) {
                pause(5000);
                if(dropMe || !isConnected) break;
                hbSent++;
                sendMessage("/hb " + (++hbSeq) + " " + System.currentTimeMillis());
                // 15 秒收不到任何心跳应答 → 服务器已死(TCP 自身对拔网线类故障要很久才报错)
                if(System.currentTimeMillis() - lastHbReply > 15000) {
                    connectionLost();
                    break;
                }
            }
        }
    }
    // 收到心跳应答(接收线程拦截后回调):刷新活性时刻,顺便算 RTT
    void onHb(String s) { // 格式: 0x01 + "HB <seq> <发送时刻>"
        lastHbReply = System.currentTimeMillis();
        try {
            StringTokenizer st = new StringTokenizer(s.substring(4));
            st.nextToken(); // seq
            long rtt = System.currentTimeMillis() - Long.parseLong(st.nextToken());
            lastRtt = rtt; hbRecv++;
            if(rtt < rttMin) rttMin = rtt;
            if(rtt > rttMax) rttMax = rtt;
            rttSum += rtt; rttCount++;
        } catch(Exception e) {}
    }
    // 发送队列积压量:分块传输据此让聊天消息插进块间隙(交织发送)
    public int pendingCount() {
        return (cms == null) ? 0 : cms.size();
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
        storeMsg("" + PUSHMARKER + "LOST");     // 通知界面:断线了(界面负责提示 + 发起自动重连)
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
    public synchronized int size() {
        return msgList.size();
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
                    // 组装明文字节:0xFD 命令标记(如有)+ UTF-8 正文;整段过加密层后 0xFF 分帧。
                    // 加密后标记在密文内部,由服务器解密后再判别
                    byte[] plain;
                    if(msg.length()>0 && msg.charAt(0) == ClientKernel.COMMAND) {
                        byte[] b = msg.substring(1).getBytes("UTF-8");
                        plain = new byte[b.length + 1];
                        plain[0] = (byte)0xFD;
                        System.arraycopy(b, 0, plain, 1, b.length);
                    } else {
                        plain = msg.getBytes("UTF-8");
                    }
                    byte[] w = com.cncd.ch04.common.CryptoUtil.wrap(plain);
                    dataOut.write(w);
                    dataOut.write(ClientKernel.MSGENDCHAR);
                    ck.addBytesOut(w.length + 1); // 流量统计(功能十五)
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
                    // 整帧收字节(0xFF 分帧/-1 断开) → 解密层还原 → UTF-8 解码。
                    // 心跳应答(0x01 HB ...)在这里拦截交给内核,不进聊天消息流
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    int c;
                    while( (c=dataIn.read()) != ClientKernel.MSGENDCHAR && c != -1) {
                        baos.write(c);
                    }
                    if(c == -1) break;
                    ck.addBytesIn(baos.size() + 1); // 流量统计(功能十五)
                    if(baos.size() == 0) continue;
                    String s = new String(com.cncd.ch04.common.CryptoUtil.unwrap(baos.toByteArray()), "UTF-8");// ← 整段一次性解码
                    if(s.length() > 3 && s.charAt(0) == ClientKernel.PUSHMARKER && s.startsWith("HB ", 1))
                        ck.onHb(s);
                    else
                        ck.storeMsg(s);
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
