package com.cncd.ch04.server;
import java.util.*;
public class BroadcastCommandParser implements CommandParser {
    private final String NICK = "nick";
    private final String USERS = "users";
    private final String EXIT = "exit";
    private final String VERSION = "version";
    private final String VERIFY = "verify";
    private final String REGISTER = "register";
    private final String WHO_AM_I = "whoami";
    private final String MSG = "msg";
    private final String STATS = "stats";
    private final String SETINFO = "setinfo";
    private final String GETINFO = "getinfo";
    private final String DELINFO = "delinfo";
    private final String ADDFRIEND = "addfriend";
    private final String DELFRIEND = "delfriend";
    private final String FRIENDS = "friends";
    private final String FILE = "file";
    private final String INFOQ = "infoq"; // 静默查询个人信息：只回机器可读推送，供界面侧栏自动拉取
    private final String FRIEND_FIELD = "friends"; // 好友名单在 DataSource 中的保留字段名
        private final String tab = "&nbsp;&nbsp;&nbsp;";
    private DataSource ds;
    private final int sek = 1000;
    private final int min = 60*sek;
    private final int hours = 60*min;
    private final int days = 24*hours;
    public BroadcastCommandParser() {
        System.out.println("BroadcastCommandParser");
    }
    public  void runCommand(ConnectedClient cc, String str) {
        try {
            if(ds == null) {
                System.out.println("CommandParser: DataSoruce Missing");
                cc.sendMessage("Server: Your command didn't get parsed, The Server Admin knows why ;)");
            } else {
                StringTokenizer strTok = new StringTokenizer(str);
                String command = strTok.nextToken();
                if(command.equalsIgnoreCase(NICK))
                    if(strTok.hasMoreTokens()) setNick(cc, strTok.nextToken());
                    else cc.sendMessage("usage: /nick <newNick>");
                else if (command.equalsIgnoreCase(USERS))
                    users(cc);
                else if (command.equalsIgnoreCase(EXIT))
                    exit(cc);
                else if (command.equalsIgnoreCase(VERIFY))
                    verifyNick(cc, strTok.nextToken());
                else if(command.equalsIgnoreCase(REGISTER))
                    registerNick(cc, strTok.nextToken(), strTok.nextToken());
                else if(command.equalsIgnoreCase(WHO_AM_I))
                    whoAmI(cc);
                else if(command.equalsIgnoreCase(MSG))
                    msg(cc, strTok.nextToken(), strTok);
                else if(command.equalsIgnoreCase(STATS))
                    stats(cc);
                else if(command.equalsIgnoreCase(SETINFO))
                    setInfo(cc, strTok);
                else if(command.equalsIgnoreCase(GETINFO))
                    getInfo(cc, strTok);
                else if(command.equalsIgnoreCase(DELINFO))
                    delInfo(cc, strTok);
                else if(command.equalsIgnoreCase(ADDFRIEND))
                    addFriend(cc, strTok);
                else if(command.equalsIgnoreCase(DELFRIEND))
                    delFriend(cc, strTok);
                else if(command.equalsIgnoreCase(FRIENDS))
                    listFriends(cc);
                else if(command.equalsIgnoreCase(FILE))
                    fileTransfer(cc, strTok);
                else if(command.equalsIgnoreCase(INFOQ))
                    pushUserInfo(cc, strTok.hasMoreTokens() ? strTok.nextToken() : cc.nick);
            }
        } catch(Exception e) {
            System.out.println("CommandParser: " + e.getMessage());
            // 回显命令内容要截断：file 命令携带整个 Base64 文件体，原样弹回会刷爆聊天区
            String shown = (str.length() > 100) ? str.substring(0, 100) + "..." : str;
            cc.sendMessage("Invalid Command: " + shown);
        }
    }
    private void stats(ConnectedClient cc) {
        long runningTime = System.currentTimeMillis() - MainServer.uptime;
        String str = "Server has been running for " + printTime(runningTime) + "<br>" + 
                     "User connects since uptime " + MainServer.connects + "<br>";
                     
                     
        cc.sendMessage(str);
    }
    private String printTime(long time) {
        String str = "";
        if(time<sek) {
            str+="" + time + "ms";
            return str;
        }
        if(time>sek && time<min) {
            long t = time%sek;
            str+="" + (time/sek) + "sek " + printTime(t);
            return str;
        }
        if(time>min && time<hours) {
            long t = time%min;
            str+= "" + (time/min) + "min " + printTime(t);
            return str;
        }
        if(time>hours && time<days) {
            long t= time%hours;
            str+= "" + (time/hours) + "hours " + printTime(t);
        }
        return str;
    }
    private void msg(ConnectedClient cc, String user, StringTokenizer strTok) {
        StringBuffer strBuff = new StringBuffer();
        while(strTok.hasMoreTokens())
            strBuff.append(strTok.nextToken() + " ");
        String body = strBuff.toString().trim();
        if(body.length() == 0) {
            cc.sendMessage("usage: /msg <user> <message>");
            return;
        }
        if(user.equalsIgnoreCase(cc.nick)) {
            cc.sendMessage("不能私聊自己");
            return;
        }
        // 私聊改为机器可读推送：接收方 0x01 PM 发送者 正文，发送方回显 0x01 PMSENT 目标 正文，
        // 客户端据此把消息路由进对应的会话窗格（会话式界面）。
        // 发送者仅在对方存在时收到回显（sendTo 找不到人时已发 "Unable to find user"）
        boolean found = cc.sendTo(user, "" + MainServer.PUSHMARKER + "PM " + cc.nick + " " + body);
        if(found)
            cc.sendMessage("" + MainServer.PUSHMARKER + "PMSENT " + user + " " + body);
    }
    private  void users(ConnectedClient cc) {
        LinkedList users = (LinkedList)((cc.getConnectionKeeper().users()).clone());
        String msg = "Current Connected Users: <br>";
        while(users.size()>0)
            msg += "*" + ((ConnectedClient)(users.removeFirst())).getNick() + "<br>";
        cc.sendMessage(msg);
    }
    private  void setNick(ConnectedClient cc, String str) {
        //System.out.println("" + cc.nick + " is now known as " + str);
        /*cc.nick = str;
        cc.sendMessage("Server: Your are now known as " + str);*/
        cc.verifyedBoolean = false;
        boolean verify = ds.verifyUser(str, "");
        if(verify) {
            if(isNickFree(cc, str)) {
                String oldNick = cc.nick;
                cc.nick = str;
                cc.verifyedBoolean = true;
                cc.sendMessage("Server: You are now known as " + str);
                cc.getConnectionKeeper().broadcastUserList();
                if(!str.equalsIgnoreCase(oldNick)) notifyFriendOnline(cc); // 同名重设不重复提醒
                pushFriends(cc); // 昵称确立后下发自己的好友名单，供界面侧栏分组
            } else
                cc.sendMessage("nick " + str + " was allready taken");
            
            
        } else {
            cc.verifyedCount = 5;
            cc.tmpNick = str;
            cc.sendMessage("Nick " + str + " is registered so you have to " +
                            "verify that this nick is yours");
        }
    }
    private boolean isNickFree(ConnectedClient cc, String nick) {
        LinkedList users = (LinkedList)((cc.getConnectionKeeper().users()).clone());
        Iterator it = users.iterator();
        while(it.hasNext()) {
            ConnectedClient comp = ((ConnectedClient)(it.next()));
            if(comp == cc) continue; // 自己与自己同名不算冲突
            String compNick = comp.getNick();
            if(nick.equalsIgnoreCase(compNick)) return false;
        }
        return true;
    }
    private void whoAmI(ConnectedClient cc) {
        cc.whoAmI();
    }
    private void registerNick(ConnectedClient cc, String nick, String pass) {
        if(pass.length()<4 || nick.length()<4) {
            cc.sendMessage("Your nick/password needs to be atleast 4 chars long");
        } else {
            if(ds.addUser(nick, pass)) {
                cc.sendMessage("User " + nick + " is now registered and set as your own");
                cc.nick = nick;
                cc.verifyedBoolean = true;
                cc.getConnectionKeeper().broadcastUserList();
                notifyFriendOnline(cc);
                pushFriends(cc);
            } else {
                cc.sendMessage("The username is allready taken");
            }
            
        }
    }
    private void verifyNick(ConnectedClient cc, String password) {
        // 注册时存的是密码 MD5，这里必须先哈希再比对（原代码传明文，永远验证失败）
        if(ds.verifyUser(cc.tmpNick, ds.getMD5(password))) {
            cc.nick = cc.tmpNick;
            cc.verifyedBoolean = true;
            cc.sendMessage("Server: nick verified, you are now known as " + cc.nick);
            notifyFriendOnline(cc);
            pushFriends(cc);
        } else {
            cc.nick = "" + cc.portNumber;
            cc.sendMessage("Invalid user/pass, your nick is set to " + cc.nick);
        }
        cc.getConnectionKeeper().broadcastUserList(); // 无论成败昵称都变了，同步名单
    }
    private  void exit(ConnectedClient cc) {
        cc.sendMessage("Server: You are being disconected!");
        try { Thread.sleep(50); } catch(Exception e) {}
        cc.dropClient();
    }
    public void setDataSource(DataSource ds) {
        this.ds = ds;
    }
    // ===== 个人信息：/setinfo /getinfo /delinfo，按当前昵称存取，服务器端落盘 =====
    private void setInfo(ConnectedClient cc, StringTokenizer strTok) {
        if(!strTok.hasMoreTokens()) { cc.sendMessage("usage: /setinfo <字段> <内容>"); return; }
        String field = strTok.nextToken();
        if(field.equalsIgnoreCase(FRIEND_FIELD)) {
            cc.sendMessage(FRIEND_FIELD + " 为系统保留字段，好友请用 /addfriend 管理"); return;
        }
        StringBuffer sb = new StringBuffer();
        while(strTok.hasMoreTokens()) {
            if(sb.length() > 0) sb.append(' ');
            sb.append(strTok.nextToken());
        }
        if(sb.length() == 0) { cc.sendMessage("usage: /setinfo <字段> <内容>"); return; }
        if(ds.addInfo(cc.nick, field, sb.toString()))
            cc.sendMessage("已保存个人信息：" + field + " = " + sb.toString());
        else
            cc.sendMessage("保存失败");
    }
    private void getInfo(ConnectedClient cc, StringTokenizer strTok) {
        String target = strTok.hasMoreTokens() ? strTok.nextToken() : cc.nick;
        String[] info = ds.getAllUserInfo(target);
        if(info.length == 0) {
            cc.sendMessage("用户 " + target + " 暂无个人信息");
        } else {
            String m = "用户 " + target + " 的个人信息：<br>";
            for(int i=0;i<info.length;i++) m += tab + info[i] + "<br>";
            cc.sendMessage(m);
        }
        pushUserInfo(cc, target); // 同步给界面侧栏一份机器可读版本
    }
    // 推送格式：0x01 USERINFO <用户> <字段: 值|字段: 值|...>（无信息时只有用户名）
    private void pushUserInfo(ConnectedClient cc, String target) {
        String[] info = ds.getAllUserInfo(target);
        StringBuffer sb = new StringBuffer();
        sb.append(MainServer.PUSHMARKER).append("USERINFO ").append(target);
        for(int i=0;i<info.length;i++)
            sb.append(i==0 ? " " : "|").append(info[i]);
        cc.sendMessage(sb.toString());
    }
    // 推送格式：0x01 FRIENDS <名字 名字 ...>（无好友时只有 FRIENDS）
    private void pushFriends(ConnectedClient cc) {
        String cur = ds.getInfo(cc.nick, FRIEND_FIELD);
        cc.sendMessage("" + MainServer.PUSHMARKER + "FRIENDS" + (cur == null ? "" : " " + cur));
    }
    private void delInfo(ConnectedClient cc, StringTokenizer strTok) {
        if(!strTok.hasMoreTokens()) { cc.sendMessage("usage: /delinfo <字段>"); return; }
        String field = strTok.nextToken();
        if(field.equalsIgnoreCase(FRIEND_FIELD)) {
            cc.sendMessage(FRIEND_FIELD + " 为系统保留字段，好友请用 /delfriend 管理"); return;
        }
        if(ds.removeInfo(cc.nick, field, null))
            cc.sendMessage("已删除个人信息：" + field);
        else
            cc.sendMessage("没有找到字段 " + field);
    }
    // ===== 好友：/addfriend /delfriend /friends，名单存为保留字段 friends（空格分隔），复用个人信息存储 =====
    private void addFriend(ConnectedClient cc, StringTokenizer strTok) {
        if(!strTok.hasMoreTokens()) { cc.sendMessage("usage: /addfriend <昵称>"); return; }
        String name = strTok.nextToken();
        if(name.equalsIgnoreCase(cc.nick)) { cc.sendMessage("不能添加自己为好友"); return; }
        String cur = ds.getInfo(cc.nick, FRIEND_FIELD);
        if(containsToken(cur, name)) { cc.sendMessage(name + " 已经在你的好友列表中"); return; }
        ds.addInfo(cc.nick, FRIEND_FIELD, (cur == null) ? name : cur + " " + name);
        cc.sendMessage("已添加好友 " + name + (isOnline(cc, name) ? "（当前在线）" : "（当前不在线，上线时会提醒你）"));
        pushFriends(cc);
    }
    private void delFriend(ConnectedClient cc, StringTokenizer strTok) {
        if(!strTok.hasMoreTokens()) { cc.sendMessage("usage: /delfriend <昵称>"); return; }
        String name = strTok.nextToken();
        String cur = ds.getInfo(cc.nick, FRIEND_FIELD);
        if(!containsToken(cur, name)) { cc.sendMessage(name + " 不在你的好友列表中"); return; }
        StringBuffer sb = new StringBuffer();
        StringTokenizer st = new StringTokenizer(cur);
        while(st.hasMoreTokens()) {
            String t = st.nextToken();
            if(t.equalsIgnoreCase(name)) continue;
            if(sb.length() > 0) sb.append(' ');
            sb.append(t);
        }
        if(sb.length() == 0) ds.removeInfo(cc.nick, FRIEND_FIELD, null);
        else ds.addInfo(cc.nick, FRIEND_FIELD, sb.toString());
        cc.sendMessage("已删除好友 " + name);
        pushFriends(cc);
    }
    private void listFriends(ConnectedClient cc) {
        String cur = ds.getInfo(cc.nick, FRIEND_FIELD);
        if(cur == null) { cc.sendMessage("你还没有好友，用 /addfriend <昵称> 添加"); return; }
        String m = "你的好友：<br>";
        StringTokenizer st = new StringTokenizer(cur);
        while(st.hasMoreTokens()) {
            String t = st.nextToken();
            m += tab + t + (isOnline(cc, t) ? "（在线）" : "（离线）") + "<br>";
        }
        cc.sendMessage(m);
    }
    private boolean containsToken(String list, String name) {
        if(list == null) return false;
        StringTokenizer st = new StringTokenizer(list);
        while(st.hasMoreTokens())
            if(st.nextToken().equalsIgnoreCase(name)) return true;
        return false;
    }
    private boolean isOnline(ConnectedClient cc, String name) {
        LinkedList users = cc.getConnectionKeeper().users(); // 已是快照
        Iterator it = users.iterator();
        while(it.hasNext())
            if(name.equalsIgnoreCase(((ConnectedClient)(it.next())).getNick())) return true;
        return false;
    }
    // 昵称确立后，向所有"把他加为好友且当前在线"的用户推送上线提醒（要求③）
    private void notifyFriendOnline(ConnectedClient cc) {
        LinkedList users = cc.getConnectionKeeper().users();
        Iterator it = users.iterator();
        while(it.hasNext()) {
            ConnectedClient u = (ConnectedClient)(it.next());
            if(u == cc) continue;
            if(containsToken(ds.getInfo(u.getNick(), FRIEND_FIELD), cc.nick))
                u.sendMessage("<font color=\"#ff8800\">你的好友 " + cc.nick + " 上线了</font>");
        }
    }
    // ===== 文件传输：/file <目标|*> <文件名> <Base64>，Base64 为纯 ASCII 不与协议字节冲突（要求⑥⑦） =====
    private void fileTransfer(ConnectedClient cc, StringTokenizer strTok) {
        if(!strTok.hasMoreTokens()) { cc.sendMessage("usage: /file <user|*> <filename> <base64>"); return; }
        String target = strTok.nextToken();
        if(!strTok.hasMoreTokens()) { cc.sendMessage("usage: /file <user|*> <filename> <base64>"); return; }
        String fname = strTok.nextToken();
        if(!strTok.hasMoreTokens()) { cc.sendMessage("usage: /file <user|*> <filename> <base64>"); return; }
        String b64 = strTok.nextToken();
        if(target.equals("*")) {
            // 群发用 FILE 推送（客户端路由进公共聊天室）
            String push = "" + MainServer.PUSHMARKER + "FILE " + cc.nick + " " + fname + " " + b64;
            LinkedList users = cc.getConnectionKeeper().users();
            Iterator it = users.iterator();
            int n = 0;
            while(it.hasNext()) {
                ConnectedClient u = (ConnectedClient)(it.next());
                if(u == cc) continue;
                u.sendMessage(push);
                n++;
            }
            cc.sendMessage("<font color=\"#9933cc\">[文件] " + fname + " 已发送给全部 " + n + " 个在线用户</font>");
        } else if(target.equalsIgnoreCase(cc.nick)) {
            cc.sendMessage("不能给自己发文件");
        } else {
            // 私发用 FILEP 推送（客户端路由进与发送者的私聊会话），回执走 PMSENT 进同一会话
            String push = "" + MainServer.PUSHMARKER + "FILEP " + cc.nick + " " + fname + " " + b64;
            if(cc.sendTo(target, push))
                cc.sendMessage("" + MainServer.PUSHMARKER + "PMSENT " + target + " [文件] " + fname + " 已发送");
        }
    }
}
