package com.cncd.ch04.server;
import java.util.*;
/**
 * 多聊天室/群组的成员管理(功能十七)。
 * 房间是"临时"的:进程内维护,空房间自动回收;成员按昵称记录,发消息时映射到在线连接。
 */
public class RoomManager {
    // 房间名(原样大小写) -> 成员昵称集合(原样)
    private final LinkedHashMap rooms = new LinkedHashMap();
    public synchronized boolean join(String room, String nick) {
        LinkedHashSet s = (LinkedHashSet)rooms.get(room);
        if(s == null) { s = new LinkedHashSet(); rooms.put(room, s); }
        return s.add(nick);
    }
    public synchronized void leave(String room, String nick) {
        LinkedHashSet s = (LinkedHashSet)rooms.get(room);
        if(s != null) { s.remove(nick); if(s.isEmpty()) rooms.remove(room); }
    }
    // 某昵称退出全部房间(下线时调用),返回它曾在的房间名,便于逐一通知
    public synchronized String[] leaveAll(String nick) {
        Vector left = new Vector();
        Iterator it = new ArrayList(rooms.keySet()).iterator();
        while(it.hasNext()) {
            String room = (String)it.next();
            LinkedHashSet s = (LinkedHashSet)rooms.get(room);
            if(s.remove(nick)) { left.add(room); if(s.isEmpty()) rooms.remove(room); }
        }
        return (String[])left.toArray(new String[0]);
    }
    public synchronized boolean isMember(String room, String nick) {
        LinkedHashSet s = (LinkedHashSet)rooms.get(room);
        return s != null && s.contains(nick);
    }
    public synchronized String[] members(String room) {
        LinkedHashSet s = (LinkedHashSet)rooms.get(room);
        return s == null ? new String[0] : (String[])s.toArray(new String[0]);
    }
    // 某昵称所在的全部房间(供客户端侧栏"群组"分组)
    public synchronized String[] roomsOf(String nick) {
        Vector v = new Vector();
        Iterator it = rooms.keySet().iterator();
        while(it.hasNext()) {
            String room = (String)it.next();
            if(((LinkedHashSet)rooms.get(room)).contains(nick)) v.add(room);
        }
        return (String[])v.toArray(new String[0]);
    }
    public synchronized String[] allRooms() {
        return (String[])rooms.keySet().toArray(new String[0]);
    }
}
