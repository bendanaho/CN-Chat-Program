package com.cncd.ch04.client;
import java.io.*;
import java.net.*;
import java.util.*;
public class ClientKernel {
    public static final char MSGENDCHAR = 0xff;
    public static final char EXIT = 0xFE;
    public static final char NICK = 0xFD;
    public static final char COMMAND = 0xFD;
    
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
        System.out.println("Drop ME!!!");
        cms.drop();
        cml.drop();
        dropMe = true;
        while(cml.hasStoped() && cms.hasStoped()) pause(5);
    }
    public void sendMessage(String str) {
        if(!dropMe) {
            if(str.charAt(0) == '/')
                cms.addMessage("" + ClientKernel.COMMAND + str.substring(1) );
            else cms.addMessage(str);
        }
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
                    char[] data = msg.toCharArray();
                    for(int i=0;i<data.length;i++) dataOut.write((int)data[i]);
                    dataOut.write(ClientKernel.MSGENDCHAR);
                }
                sleep(10);
            }
            dataOut.write(ClientKernel.EXIT);
            dataOut.close();
            stop();
        } catch(Exception ioe) {
            ioe.printStackTrace();
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
                    StringBuffer strBuff = new StringBuffer();
                    int c;
                    while( (c=dataIn.read()) != ClientKernel.MSGENDCHAR) {
                        strBuff.append((char)c);
                    }
                    ck.storeMsg("" + strBuff.toString());
                }
                dataIn.close();
                buffIn.close();
                stop();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        } finally {
            hasStoped = true;
        }
    }
}
