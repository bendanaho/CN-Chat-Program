package com.cncd.ch04.server;
import java.io.*;
import java.net.*;
import java.util.*;
public class ConnectionKeeper {
    private LinkedList clientList;
    private CommandParser cp;
    public ConnectionKeeper(CommandParser parser) {
        this.cp = parser;
        clientList = new LinkedList();
    }
    // 连接池被多个线程并发读写（主线程加人、各连接线程踢人/广播），
    // 全部入口加 synchronized 互斥，否则可能漏发消息或抛 ConcurrentModificationException
    public synchronized void add(Socket s) {
        MainServer.connects++;
        clientList.addLast(new ConnectedClient(s, this));
        broadcastUserList();
    }
    public synchronized void remove(ConnectedClient cc) {
        clientList.remove(cc);
        cc = null;
        broadcastUserList();
    }
    // 向所有在线客户端推送当前名单，格式：0x01 + "USERLIST" + 空格分隔的昵称
    // （昵称经 StringTokenizer 按空白切分而来，必不含空格，用空格分隔安全）
    public synchronized void broadcastUserList() {
        StringBuffer sb = new StringBuffer();
        sb.append(MainServer.PUSHMARKER).append("USERLIST");
        for(int i=0;i<clientList.size();i++)
            sb.append(' ').append(((ConnectedClient)clientList.get(i)).getNick());
        String msg = sb.toString();
        for(int i=0;i<clientList.size();i++)
            ((ConnectedClient)clientList.get(i)).sendMessage(msg);
    }
    public synchronized LinkedList users() {
        return (LinkedList)clientList.clone(); // 返回快照，调用方遍历时不受池变动影响
    }
    public void runCommand(ConnectedClient cc, String str) {
        cp.runCommand(cc, str);
    }
    // 返回是否找到了目标用户，调用方据此决定要不要给发送者回显
    public synchronized boolean sendTo(ConnectedClient sender, String user, String msg) {
        boolean found = false;
        for(int i =0;i<clientList.size();i++) {
            ConnectedClient receiver = (ConnectedClient)(clientList.get(i));
            if(user.equalsIgnoreCase(receiver.nick)) {
                receiver.sendMessage(msg);
                found = true;
                i = clientList.size()+5; // Stop the loop.
            }
        }
        if(!found) {
            sender.sendMessage("Unable to find user " + user);
        }
        return found;
    }
    public synchronized void broadcast(String msg) {
        for(int i =0;i<clientList.size();i++) {
            ConnectedClient cc = (ConnectedClient)(clientList.get(i));
            cc.sendMessage(msg);
        }
    }
}
