package com.cncd.ch04.server;
public interface DataSource {
    public boolean verifyUser(String user, String pass);
    public boolean addUser(String user, String pass);
    public boolean removeUser(String user, String pass);
    public boolean addInfo(String user, String field, String info);
    public boolean removeInfo(String user, String field, String info);
    public String[] getAllUserInfo(String user);
    public String getInfo(String user, String field);
    public String[] getUserList();
    public String getMD5(String str);
    public boolean isRegistered(String user);            // 该昵称是否已注册(功能十六判断能否留言)
    public void addOffline(String to, String from, String text); // 存一条离线消息(功能十六)
    public String[] takeOffline(String to);              // 取走并清空某人的离线消息,元素="from|time|text"
}
