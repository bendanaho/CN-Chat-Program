package com.cncd.ch04.server;
import java.net.*;
import java.io.*;
public class MainServer extends Thread {
    public static final String DISCONNECTED = "Software caused connection abort";
    public static final String DISCONNECTED_CLIENT = "Socket closed";
    public static final String PORT_USED_ERROR = "Address already in use: JVM_Bind";
    public static long uptime = 0; 
    public static long connects = 0;
    public static final char MSGENDCHAR = 0xff;
    // 服务器主动推送（如在线名单）的标记字符：控制字符用户打不出来，
    // 且聊天广播都带 "昵称:" 前缀，客户端凭首字符即可无歧义识别推送
    public static final char PUSHMARKER = 0x01;
    public static final int DISCOVERY_PORT = 3600; // UDP 服务器发现端口(功能十)
    public static final long HEARTBEAT_TIMEOUT = 30 * 1000; // 超过此时长没收到任何消息判死连接(功能十一)
    // 消息唯一 ID(功能十二协议升级引入):群聊/私聊每条消息可寻址,是回执/撤回的前置
    private static long msgId = 0;
    public static synchronized long nextMsgId() { return ++msgId; }
    int port = 1984;
    int clients = 8;
    private ServerSocket sSock;
    private Socket sock;
    public ConnectionKeeper ck;
    public static DataSource ds;
    public static CommandParser cp;
    public MainServer(int port) {
        this.port = port;
        ck = new ConnectionKeeper(MainServer.cp);
        MainServer.uptime = System.currentTimeMillis();
        start();
    }
    public void run() {
        while(true) {
            try {
                sSock = new ServerSocket(port);
                System.out.println("Server Listening at port: " + sSock.getLocalPort());
                // 在同一个 ServerSocket 上持续 accept。原代码每接受一个连接就关闭
                // 重建监听 socket，关闭瞬间 backlog 里排队的连接请求会被直接重置，
                // 多个客户端同时连入时后到的被拒（实测 3 个并发连接掉 2 个）
                while(true) {
                    sock = sSock.accept();
                    ck.add(sock);
                }
            } catch(Exception e) {
                String message = e.getMessage();
                if(message == null) message = "";
                // JDK17 的报错是 "Address already in use: bind"，老 JDK 是 "...: JVM_Bind"，
                // 用 contains 兼容两者，否则端口被占时不会换端口而是直接退出
                if(message.contains("Address already in use")) {
                    System.out.println("Port " + port + " is already used, Attempting to use " + (port+=1));
                } else {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }
    public static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch(Exception e) {}
    }
    // 打印本机所有局域网 IPv4 地址,别的机器照着填 Host(功能十·部署基础)
    private static void printLanIps() {
        try {
            System.out.println("本机局域网地址(其他电脑的客户端 Host 填其一,或直接点\"扫描局域网\"):");
            java.util.Enumeration nis = NetworkInterface.getNetworkInterfaces();
            while(nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface)nis.nextElement();
                if(!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration ads = ni.getInetAddresses();
                while(ads.hasMoreElements()) {
                    InetAddress a = (InetAddress)ads.nextElement();
                    if(a instanceof Inet4Address)
                        System.out.println("  " + a.getHostAddress() + "  (" + ni.getDisplayName() + ")");
                }
            }
        } catch(Exception e) { System.out.println("枚举网卡失败: " + e.getMessage()); }
    }
    public static void main(String arg[]) {
        int port = 0;
        MainServer ms;
        MainServer.ds = new FileDataSource();
        MainServer.cp = new BroadcastCommandParser();
        MainServer.cp.setDataSource(MainServer.ds);
        System.out.println("加密传输: " + (com.cncd.ch04.common.CryptoUtil.ENABLED ? "开启(AES)" : "关闭(明文, -Dchat.plain=true)"));
        printLanIps();
        if(arg.length!=1) {
            ms = new MainServer(3500);
            System.out.println("Usage: java jchat.server.MainServer <port>\nAttempting to use default port 3500");
        } else {
            try {
                port = Integer.parseInt(arg[0]);
            } catch(NumberFormatException nfe) {
                System.out.println("Attempting to use default port 3500");
                port = 3500;
            } finally {
                ms = new MainServer(port);
            }
        }
        new DiscoveryResponder(ms).start();
        new HeartbeatSweeper(ms).start();
    }
}
// UDP 服务器发现应答(功能十):监听广播端口,收到探测包即回自己的 TCP 端口;
// 客户端从应答包的源地址得知服务器 IP —— 为项目引入 TCP 之外的第二种 socket
class DiscoveryResponder extends Thread {
    private MainServer ms;
    public DiscoveryResponder(MainServer ms) { this.ms = ms; setDaemon(true); }
    public void run() {
        try {
            DatagramSocket sock = new DatagramSocket(MainServer.DISCOVERY_PORT);
            System.out.println("UDP 发现服务已就绪(端口 " + MainServer.DISCOVERY_PORT + ")");
            byte[] buf = new byte[128];
            while(true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                sock.receive(p); // 阻塞等广播
                String q = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                if("CHAT_DISCOVER".equals(q)) {
                    byte[] resp = ("CHAT_SERVER " + ms.port).getBytes("UTF-8");
                    sock.send(new DatagramPacket(resp, resp.length, p.getAddress(), p.getPort()));
                }
            }
        } catch(Exception e) {
            System.out.println("UDP 发现服务不可用(可能端口被另一个服务器占用): " + e.getMessage());
        }
    }
}
// 心跳清扫(功能十一):周期检查每个连接最后一次收到消息的时间,
// 超时视为"半死连接"(拔网线/睡眠等 TCP 无法及时感知的情况)并清理
class HeartbeatSweeper extends Thread {
    private MainServer ms;
    public HeartbeatSweeper(MainServer ms) { this.ms = ms; setDaemon(true); }
    public void run() {
        while(true) {
            MainServer.sleep(10000);
            java.util.LinkedList users = ms.ck.users(); // 快照
            java.util.Iterator it = users.iterator();
            long now = System.currentTimeMillis();
            while(it.hasNext()) {
                ConnectedClient c = (ConnectedClient)(it.next());
                if(now - c.lastHeard > MainServer.HEARTBEAT_TIMEOUT) {
                    System.out.println("HeartbeatSweeper: " + c.nick + " 心跳超时,清理连接");
                    c.dropClient();
                }
            }
        }
    }
}
