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
    private final String HB = "hb";           // 心跳(功能十一):原样回显 seq 和时间戳,客户端据此测活/测RTT
    private final String FSTART = "fstart";   // 分块传输(功能十二)三连:开始/数据块/结束
    private final String FCHUNK = "fchunk";
    private final String FEND = "fend";
    // 进行中的分块传输:tid -> {目标("*"或昵称), 发送者连接}。服务器只做路由中继,不落地文件
    private final HashMap transfers = new HashMap();
    // 阶段二命令
    private final String ENTER = "enter";     // 准入口令(功能十八)
    private final String JOIN = "join", PART = "part", ROOMS = "rooms", ROOM = "room", ROOMMEM = "roommem"; // 群组(功能十七)
    private final String KICK = "kick", MUTE = "mute", UNMUTE = "unmute", ANNOUNCE = "announce"; // 管理(功能十八)
    private final String READ = "read", TYPING = "typing"; // 已读回执/正在输入(功能十九)
    private final String RECALL = "recall";   // 消息撤回(功能二十)
    private final String AVATAR = "avatar";   // 头像上传(功能二十二):Base64 PNG,存保留字段并全服分发
    private final String SHAKE = "shake";     // 窗口抖动(功能二十四)
    private final String AVATAR_FIELD = "avatar"; // 头像在 DataSource 中的保留字段名
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
                else if(command.equalsIgnoreCase(HB))
                    heartbeat(cc, strTok);
                else if(command.equalsIgnoreCase(FSTART))
                    fstart(cc, strTok);
                else if(command.equalsIgnoreCase(FCHUNK))
                    fchunk(cc, strTok);
                else if(command.equalsIgnoreCase(FEND))
                    fend(cc, strTok);
                else if(command.equalsIgnoreCase(ENTER))
                    enter(cc, strTok);
                else if(command.equalsIgnoreCase(JOIN))
                    join(cc, strTok);
                else if(command.equalsIgnoreCase(PART))
                    part(cc, strTok);
                else if(command.equalsIgnoreCase(ROOMS))
                    listRooms(cc);
                else if(command.equalsIgnoreCase(ROOM))
                    room(cc, strTok);
                else if(command.equalsIgnoreCase(ROOMMEM))
                    pushMembers(cc, strTok.hasMoreTokens() ? strTok.nextToken() : "");
                else if(command.equalsIgnoreCase(KICK))
                    kick(cc, strTok);
                else if(command.equalsIgnoreCase(MUTE))
                    setMute(cc, strTok, true);
                else if(command.equalsIgnoreCase(UNMUTE))
                    setMute(cc, strTok, false);
                else if(command.equalsIgnoreCase(ANNOUNCE))
                    announce(cc, strTok);
                else if(command.equalsIgnoreCase(READ))
                    readReceipt(cc, strTok);
                else if(command.equalsIgnoreCase(TYPING))
                    typing(cc, strTok);
                else if(command.equalsIgnoreCase(RECALL))
                    recall(cc, strTok);
                else if(command.equalsIgnoreCase(AVATAR))
                    setAvatar(cc, strTok);
                else if(command.equalsIgnoreCase(SHAKE))
                    shake(cc, strTok);
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
        if(MainServer.isMuted(cc.nick)) { cc.muteNotice(); return; } // 禁言覆盖私聊(功能十八)
        // 私聊为机器可读推送(带消息唯一 ID)：接收方 0x01 PM <id> 发送者 正文，
        // 发送方回显 0x01 PMSENT <id> 目标 正文。id 记录作用域=目标昵称,供已读回执/撤回定位
        long id = MainServer.nextMsgId();
        MainServer.recordMsg(id, cc.nick, user);
        boolean found = cc.sendTo(user, "" + MainServer.PUSHMARKER + "PM " + id + " " + cc.nick + " " + body);
        if(found) {
            cc.sendMessage("" + MainServer.PUSHMARKER + "PMSENT " + id + " " + user + " " + body);
        } else if(ds.isRegistered(user)) {
            // 对方离线但已注册 → 存为离线消息,上线验证身份后补发(功能十六)
            ds.addOffline(user, cc.nick, body);
            // 给发送方回一条离线回显 PMOFF:与在线时的 PMSENT 一样在会话里显示自己发的气泡,
            // 只是状态标注"已暂存,对方上线后送达"(此前只回纯文本提示,发送方看不到自己发的消息)
            cc.sendMessage("" + MainServer.PUSHMARKER + "PMOFF " + id + " " + user + " " + body);
        }
        // 未注册的离线用户:sendTo 已提示 "Unable to find user"
    }
    // 上线并确立身份后,补发暂存的离线消息(功能十六);仅对已验证的注册昵称投递
    private void deliverOffline(ConnectedClient cc) {
        if(!cc.verifyedBoolean) return; // 未通过密码验证的昵称不收留言,防冒领
        String[] ms = ds.takeOffline(cc.nick);
        for(int i=0;i<ms.length;i++) {
            String[] p = ms[i].split("\\|", 3); // from|time|text
            if(p.length < 3) continue;
            String t = new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(Long.parseLong(p[1])));
            long id = MainServer.nextMsgId();
            MainServer.recordMsg(id, p[0], cc.nick);
            cc.sendMessage("" + MainServer.PUSHMARKER + "PM " + id + " " + p[0]
                    + " <font color=\"#9933cc\">[离线消息 " + t + "]</font> " + p[2]);
        }
    }
    // 心跳应答(功能十一):把客户端发来的 seq/时间戳原样推回,一包两用——测活 + 测 RTT
    private void heartbeat(ConnectedClient cc, StringTokenizer strTok) {
        StringBuffer sb = new StringBuffer();
        sb.append(MainServer.PUSHMARKER).append("HB");
        while(strTok.hasMoreTokens()) sb.append(' ').append(strTok.nextToken());
        cc.sendMessage(sb.toString());
    }
    // ===== 分块传输中继(功能十二):服务器不落地文件,只按 tid 查路由逐块转发 =====
    private void fstart(ConnectedClient cc, StringTokenizer st) {
        String target = st.nextToken(), tid = st.nextToken(), fname = st.nextToken();
        String size = st.nextToken(), count = st.nextToken();
        if(target.equalsIgnoreCase(cc.nick)) { cc.sendMessage("不能给自己发文件"); return; }
        if(MainServer.isMuted(cc.nick)) { cc.muteNotice(); return; } // 禁言覆盖文件/图片/语音/自定义表情(功能十八)
        // 给这次文件传输分配消息 ID 并记录作用域(群发=*、私发=对方昵称),
        // 使文件/图片/表情也能像文字消息一样撤回(功能二十);ID 随 FSTART 下发给接收方,
        // 另用 FID 回传给发送方 —— 双方据此把文件气泡挂上可撤回的 ID。
        long id = MainServer.nextMsgId();
        String recallScope = target.equals("*") ? "*" : target;
        MainServer.recordMsg(id, cc.nick, recallScope);
        String head = "" + MainServer.PUSHMARKER + "FSTART " + cc.nick + " ";
        String tail = tid + " " + id + " " + fname + " " + size + " " + count;
        boolean routed;
        if(target.equals("*")) { pushToOthers(cc, head + "B " + tail); routed = true; }
        else routed = cc.sendTo(target, head + "P " + tail);
        if(routed) {
            synchronized(transfers) { transfers.put(tid, target); }
            cc.sendMessage("" + MainServer.PUSHMARKER + "FID " + tid + " " + id);
        }
    }
    private void fchunk(ConnectedClient cc, StringTokenizer st) {
        String tid = st.nextToken(), idx = st.nextToken(), b64 = st.nextToken();
        routeChunk(cc, tid, "" + MainServer.PUSHMARKER + "FCHUNK " + tid + " " + idx + " " + b64, false);
    }
    private void fend(ConnectedClient cc, StringTokenizer st) {
        String tid = st.nextToken();
        routeChunk(cc, tid, "" + MainServer.PUSHMARKER + "FEND " + tid, true);
    }
    private void routeChunk(ConnectedClient cc, String tid, String push, boolean end) {
        String target;
        synchronized(transfers) {
            target = (String)transfers.get(tid);
            if(end) transfers.remove(tid);
        }
        if(target == null) return; // 未知 tid(可能 fstart 失败),静默丢弃
        if("*".equals(target)) pushToOthers(cc, push);
        else cc.sendTo(target, push); // 目标中途下线时 sendTo 会提示发送者
    }
    private void pushToOthers(ConnectedClient cc, String msg) {
        LinkedList users = cc.getConnectionKeeper().users(); // 快照
        Iterator it = users.iterator();
        while(it.hasNext()) {
            ConnectedClient u = (ConnectedClient)(it.next());
            if(u != cc) u.sendMessage(msg);
        }
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
                pushAvatars(cc);
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
                deliverOffline(cc);
                pushRooms(cc);
                pushFriends(cc);
                pushAvatars(cc);
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
            deliverOffline(cc);
            pushRooms(cc);
            pushAvatars(cc);
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
    // ===== 准入口令(功能十八) =====
    private void enter(ConnectedClient cc, StringTokenizer st) {
        String p = st.hasMoreTokens() ? st.nextToken() : "";
        if(p.equals(MainServer.ENTRYPASS)) {
            cc.entered = true;
            cc.sendMessage("<font color=\"#008800\">口令正确,已进入聊天室</font>");
        } else {
            cc.sendMessage("<font color=\"#cc0000\">口令错误,请重试: /enter &lt;口令&gt;</font>");
        }
    }
    // ===== 群组(功能十七) =====
    private void join(ConnectedClient cc, StringTokenizer st) {
        if(!st.hasMoreTokens()) { cc.sendMessage("usage: /join <群名>"); return; }
        String room = st.nextToken();
        MainServer.rooms.join(room, cc.nick);
        cc.sendMessage("<font color=\"#008800\">已加入群组 [" + room + "]</font>");
        roomNotice(cc, room, cc.nick + " 加入了群组");
        pushRooms(cc);
        pushMembersToRoom(cc, room); // 全体成员的群成员栏刷新
    }
    private void part(ConnectedClient cc, StringTokenizer st) {
        if(!st.hasMoreTokens()) { cc.sendMessage("usage: /part <群名>"); return; }
        String room = st.nextToken();
        MainServer.rooms.leave(room, cc.nick);
        cc.sendMessage("已退出群组 [" + room + "]");
        roomNotice(cc, room, cc.nick + " 退出了群组");
        pushRooms(cc);
        pushMembersToRoom(cc, room); // 通知剩余成员刷新群成员栏
    }
    private void pushMembers(ConnectedClient cc, String room) {
        String[] m = MainServer.rooms.members(room);
        StringBuffer sb = new StringBuffer("" + MainServer.PUSHMARKER + "ROOMMEM " + room);
        for(int i=0;i<m.length;i++) sb.append(' ').append(m[i]);
        cc.sendMessage(sb.toString());
    }
    private void pushMembersToRoom(ConnectedClient any, String room) {
        String[] m = MainServer.rooms.members(room);
        StringBuffer sb = new StringBuffer("" + MainServer.PUSHMARKER + "ROOMMEM " + room);
        for(int i=0;i<m.length;i++) sb.append(' ').append(m[i]);
        sendToRoom(room, sb.toString(), any);
    }
    private void listRooms(ConnectedClient cc) {
        String[] all = MainServer.rooms.allRooms();
        if(all.length == 0) { cc.sendMessage("当前没有群组,用 /join <群名> 创建"); return; }
        String m = "现有群组:<br>";
        for(int i=0;i<all.length;i++)
            m += tab + all[i] + "(" + MainServer.rooms.members(all[i]).length + " 人)<br>";
        cc.sendMessage(m);
    }
    private void room(ConnectedClient cc, StringTokenizer st) {
        if(!st.hasMoreTokens()) return;
        String room = st.nextToken();
        if(!MainServer.rooms.isMember(room, cc.nick)) { cc.sendMessage("你不在群组 [" + room + "],先 /join"); return; }
        StringBuffer sb = new StringBuffer();
        while(st.hasMoreTokens()) { if(sb.length()>0) sb.append(' '); sb.append(st.nextToken()); }
        if(sb.length() == 0) return;
        long id = MainServer.nextMsgId();
        String push = "" + MainServer.PUSHMARKER + "ROOM " + id + " " + room + " " + cc.nick + " " + sb;
        if(MainServer.isMuted(cc.nick)) { cc.muteNotice(); return; } // 禁言:丢弃并提示未发送,不回显假气泡
        MainServer.recordMsg(id, cc.nick, "#" + room);
        sendToRoom(room, push, cc);
    }
    // 发给某房间的全部在线成员
    private void sendToRoom(String room, String push, ConnectedClient any) {
        LinkedList users = any.getConnectionKeeper().users();
        Iterator it = users.iterator();
        while(it.hasNext()) {
            ConnectedClient u = (ConnectedClient)(it.next());
            if(MainServer.rooms.isMember(room, u.getNick())) u.sendMessage(push);
        }
    }
    private void roomNotice(ConnectedClient actor, String room, String text) {
        sendToRoom(room, "" + MainServer.PUSHMARKER + "ROOMSYS " + room + " " + text, actor);
    }
    // 下发某人所在的群组列表 → 客户端侧栏"群组"分组
    private void pushRooms(ConnectedClient cc) {
        String[] rs = MainServer.rooms.roomsOf(cc.nick);
        StringBuffer sb = new StringBuffer("" + MainServer.PUSHMARKER + "ROOMS");
        for(int i=0;i<rs.length;i++) sb.append(' ').append(rs[i]);
        cc.sendMessage(sb.toString());
    }
    // ===== 管理员(功能十八):踢人/禁言/公告 =====
    private boolean isAdmin(ConnectedClient cc) {
    // 新增：硬编码 penguin 和 Brendan 直接拥有管理员权限
    if (cc.nick.equalsIgnoreCase("penguin") || cc.nick.equalsIgnoreCase("Brendan")) {
        return true;
    }
    return MainServer.ADMIN.length() > 0 && cc.nick.equalsIgnoreCase(MainServer.ADMIN);
}
    private void kick(ConnectedClient cc, StringTokenizer st) {
        if(!isAdmin(cc)) { cc.sendMessage("需要管理员权限"); return; }
        if(!st.hasMoreTokens()) { cc.sendMessage("usage: /kick <用户>"); return; }
        String target = st.nextToken();
        boolean done = false;
        LinkedList users = cc.getConnectionKeeper().users();
        Iterator it = users.iterator();
        while(it.hasNext()) {
            ConnectedClient u = (ConnectedClient)(it.next());
            if(u.getNick().equalsIgnoreCase(target)) {
                u.sendMessage("<font color=\"#cc0000\">你已被管理员踢出聊天室</font>");
                try { Thread.sleep(60); } catch(Exception e) {}
                u.dropClient();
                done = true;
            }
        }
        cc.sendMessage(done ? "已踢出 " + target : "用户 " + target + " 不在线");
    }
    private void setMute(ConnectedClient cc, StringTokenizer st, boolean mute) {
        if(!isAdmin(cc)) { cc.sendMessage("需要管理员权限"); return; }
        if(!st.hasMoreTokens()) { cc.sendMessage("usage: /" + (mute?"mute":"unmute") + " <用户>"); return; }
        String target = st.nextToken();
        synchronized(MainServer.muted) {
            if(mute) MainServer.muted.add(target.toLowerCase());
            else MainServer.muted.remove(target.toLowerCase());
        }
        cc.sendMessage((mute ? "已禁言 " : "已解除禁言 ") + target);
        LinkedList users = cc.getConnectionKeeper().users();
        Iterator it = users.iterator();
        while(it.hasNext()) {
            ConnectedClient u = (ConnectedClient)(it.next());
            if(u.getNick().equalsIgnoreCase(target))
                u.sendMessage("<font color=\"#ff8800\">你已被管理员" + (mute ? "禁言" : "解除禁言") + "</font>");
        }
    }
    private void announce(ConnectedClient cc, StringTokenizer st) {
        if(!isAdmin(cc)) { cc.sendMessage("需要管理员权限"); return; }
        StringBuffer sb = new StringBuffer();
        while(st.hasMoreTokens()) { if(sb.length()>0) sb.append(' '); sb.append(st.nextToken()); }
        if(sb.length() == 0) return;
        cc.getConnectionKeeper().broadcast("<font color=\"#ff8800\"><b>[公告] " + sb + "</b></font>");
    }
    // ===== 已读回执 / 正在输入(功能十九) =====
    private void pushToNick(ConnectedClient any, String nick, String msg) {
        LinkedList users = any.getConnectionKeeper().users();
        Iterator it = users.iterator();
        while(it.hasNext()) {
            ConnectedClient u = (ConnectedClient)(it.next());
            if(u.getNick().equalsIgnoreCase(nick)) u.sendMessage(msg);
        }
    }
    private void readReceipt(ConnectedClient cc, StringTokenizer st) {
        if(!st.hasMoreTokens()) return;
        long id;
        try { id = Long.parseLong(st.nextToken()); } catch(Exception e) { return; }
        String[] info = MainServer.msgInfo(id); // [发送者, 时刻, 作用域]
        if(info == null || !info[2].equalsIgnoreCase(cc.nick)) return; // 只有该私聊的接收方能回执
        pushToNick(cc, info[0], "" + MainServer.PUSHMARKER + "READ " + id); // 通知原发送者
    }
    private void typing(ConnectedClient cc, StringTokenizer st) {
        if(!st.hasMoreTokens()) return;
        pushToNick(cc, st.nextToken(), "" + MainServer.PUSHMARKER + "TYPING " + cc.nick);
    }
    // ===== 头像(功能二十二):存保留字段,变更即全服分发;上线时双向同步 =====
    private void setAvatar(ConnectedClient cc, StringTokenizer st) {
        if(!st.hasMoreTokens()) return;
        String b64 = st.nextToken();
        if(b64.length() > 300000) { cc.sendMessage("头像过大,请选小一点的图片"); return; } // 128px PNG 足够
        ds.addInfo(cc.nick, AVATAR_FIELD, b64);
        cc.getConnectionKeeper().broadcast("" + MainServer.PUSHMARKER + "AVATAR " + cc.nick + " " + b64);
    }
    // 上线确立昵称时:把所有在线者的头像发给新人,把新人的头像发给所有人
    private void pushAvatars(ConnectedClient cc) {
        LinkedList users = cc.getConnectionKeeper().users();
        Iterator it = users.iterator();
        while(it.hasNext()) {
            ConnectedClient u = (ConnectedClient)(it.next());
            String av = ds.getInfo(u.getNick(), AVATAR_FIELD);
            if(av != null) cc.sendMessage("" + MainServer.PUSHMARKER + "AVATAR " + u.getNick() + " " + av);
        }
        String mine = ds.getInfo(cc.nick, AVATAR_FIELD);
        if(mine != null)
            cc.getConnectionKeeper().broadcast("" + MainServer.PUSHMARKER + "AVATAR " + cc.nick + " " + mine);
    }
    // ===== 窗口抖动(功能二十四):转发给目标 =====
    private void shake(ConnectedClient cc, StringTokenizer st) {
        if(!st.hasMoreTokens()) return;
        pushToNick(cc, st.nextToken(), "" + MainServer.PUSHMARKER + "SHAKE " + cc.nick);
    }
    // ===== 消息撤回(功能二十) =====
    private void recall(ConnectedClient cc, StringTokenizer st) {
        if(!st.hasMoreTokens()) return;
        long id;
        try { id = Long.parseLong(st.nextToken()); } catch(Exception e) { return; }
        String[] info = MainServer.msgInfo(id);
        if(info == null) { cc.sendMessage("消息不存在或已过期,无法撤回"); return; }
        if(!info[0].equalsIgnoreCase(cc.nick)) { cc.sendMessage("只能撤回自己的消息"); return; }
        if(System.currentTimeMillis() - Long.parseLong(info[1]) > MainServer.RECALL_LIMIT) {
            cc.sendMessage("超过 2 分钟,无法撤回"); return;
        }
        String scope = info[2];
        String push = "" + MainServer.PUSHMARKER + "RECALL " + id + " " + cc.nick;
        if(scope.equals("*")) cc.getConnectionKeeper().broadcast(push);            // 群聊:广播
        else if(scope.startsWith("#")) sendToRoom(scope.substring(1), push, cc);   // 群组
        else { pushToNick(cc, scope, push); cc.sendMessage(push); }                // 私聊:对方+自己
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
        if(field.equalsIgnoreCase(AVATAR_FIELD)) {
            cc.sendMessage(AVATAR_FIELD + " 为系统保留字段，头像请用界面按钮设置"); return;
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
        if(MainServer.isMuted(cc.nick)) { cc.muteNotice(); return; } // 禁言覆盖单包文件(功能十八)
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
