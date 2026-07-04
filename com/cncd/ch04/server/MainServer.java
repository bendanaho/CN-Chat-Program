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
    public static void main(String arg[]) {
        int port = 0;
        MainServer ms;
        MainServer.ds = new FileDataSource();
        MainServer.cp = new BroadcastCommandParser();
        MainServer.cp.setDataSource(MainServer.ds);
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
    }
}
