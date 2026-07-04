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
    JButton buttonConnect, buttonSend;
    JScrollPane sc;
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
        buttonSend.addActionListener(this);
        msgWindow.addKeyListener(this);
        add(southPanel, BorderLayout.SOUTH);
        //创建Center
        historyWindow = new ClientHistory();
        sc = new JScrollPane(historyWindow);
        sc.setAutoscrolls(true);
        this.add(sc, BorderLayout.CENTER);
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
        historyWindow.addText(str);
    }
    private void connect() {
        try {
            if(ck!=null) ck.dropMe();
            ck = new ClientKernel(txtHost.getText(), Integer.parseInt(txtPort.getText()));
            ck.setNick(txtNick.getText());
            if(ck.isConnected()) {
                ck.addClient(this);
                addMsg("<font color=\"#00ff00\">connected! Local Port:" + ck.getLocalPort() + "</font>");
            } else {
                addMsg("<font color=\"#ff0000\">connect failed！</font>");
            }
        } catch(Exception e) { e.printStackTrace(); }
    }
    private void send() {
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
            if(e.getSource() == txtNick) { connect(); msgWindow.requestFocus(); }
            if(e.getSource() == txtHost) txtPort.requestFocus();
            if(e.getSource() == txtPort) txtNick.requestFocus();
        }
    }
    public void actionPerformed(ActionEvent e) {
        if(e.getSource()==buttonConnect) connect();
        if(e.getSource()==buttonSend) send();
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

