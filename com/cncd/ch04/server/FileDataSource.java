package com.cncd.ch04.server;
import java.util.*;
import java.security.*;
import java.io.*;
public class FileDataSource implements DataSource {
    String[] us = {"no connected users"}; // An array of all users.
    boolean userListChanged = true;
    boolean debug = false;
    LinkedList users = new LinkedList();
    LinkedList infoList = new LinkedList();
    String pathSep = System.getProperty("file.separator");
    String fsroot = System.getProperty("user.home") + pathSep 
                        + ".mihalychat" + pathSep;
    File userFile, infoFile;
    boolean basicWrite = false;
    boolean allWrite = false;
    private final String[] infoComments = {"# Info file for Chat Server",
                             "# Used when the server is running in FileDataSource mode",
                             "# <username> <field> <data>",
                             "# username is the name of the user which the info belongs to"};
                             
    private final String[] userComments = {"# User file for Mihaly Chat Server",
                             "# <username> <password>",
                             "# usernames are in clear text",
                             "# passwords are MD5 hashes"};
     public FileDataSource() {
        System.out.println("FileDataSource init");
        initFileSystem();
        if(allWrite) {
            readFiles();
        }  
    }
    public void printAll() {
        if(debug)System.out.println("\nStaring to print");
        Iterator it = users.iterator();
        while(it.hasNext())
            ((User)(it.next())).printUser();
        it = infoList.iterator();
        while(it.hasNext())
            ((Info)(it.next())).printInfo();
        if(debug)System.out.println("Ending print\n");
    }
    private void initFileSystem() {
        File file = new File(fsroot);
        if(file.exists() && file.isDirectory()) {
            if(debug)System.out.println("FileDataSource home " + fsroot);
            basicWrite = true;
        } else {
            System.out.println("FileDataSource is unable to access home" + 
                                "\nAttempting to create home");
            file.mkdir();
            if( !(file.exists() && file.isDirectory()) ) 
                System.out.println("FileDataSource is unable to create home @ " 
                        + fsroot + " ex " + file.exists() + " isDir "
                        + file.isDirectory());
            else {
                System.out.println("FileDataSource home created " + fsroot);
                basicWrite = true;
            }
        }
        if(basicWrite) {
            userFile = new File(fsroot + "users");
            infoFile = new File(fsroot + "info");
            if(userFile.exists() && infoFile.exists()) {
                if(userFile.canRead() && userFile.canWrite() 
                    && infoFile.canRead() && infoFile.canWrite()) {
                    if(debug) System.out.println("FileDataSource userfile " + userFile + 
                        "\nFileDataSource infofile " + infoFile);
                    allWrite = true;
                } else {
                    System.out.println("FileDataSource files exist but" +
                        "userFile.read "  + userFile.canRead() +
                        "userFile.write "  + userFile.canWrite() +
                        "infoFile.read "  + infoFile.canRead() +
                        "userFile.write "  + userFile.canWrite());
                }
            } else {
                try {
                    userFile.createNewFile();
                    infoFile.createNewFile();
                    if(userFile.exists() && infoFile.exists()) {
                        if(userFile.canWrite() && infoFile.canWrite()) {
                            if(debug) System.out.println("FileDataSource userfile " 
                                + userFile + "\nFileDataSource infofile " 
                                + infoFile);
                            createFiles();
                            allWrite = true;
                        } else {
                            System.out.println("FileDataSource user/info files " + 
                                "we just created are not writeable :(");
                        }
                    } else {
                        System.out.println("FileDataSource you shouldn't get " +
                            "here but you did. The user/info file that we just" +
                            "created are gone missing");
                    }
                } catch(IOException ioe) {
                    System.out.println("FileDataSource " + ioe.getMessage());
                }
            }
        }      
    }
    private void createFiles() {
        for(int i=0;i<userComments.length;i++)
            appendToUserFile(userComments[i]);        
        
        for(int i=0;i<infoComments.length;i++)
            appendToInfoFile(infoComments[i]);
    }
    private void readFiles() {
        
        Object[] ObjUsers = readLines(userFile);
        Object[] ObjInfo  = readLines(infoFile);
        users = new LinkedList();
        infoList = new LinkedList();
        try {
            for(int i = 0;i<ObjUsers.length;i++) {
                String str = "" + ObjUsers[i];
                String name = str.substring(0, str.indexOf(" "));
                String pass = str.substring(str.indexOf(" ")+1);
                users.add(new User(name,pass));
            }
            for(int i = 0;i<ObjInfo.length;i++) {
                // 格式：<用户> <字段> <值>，值允许含空格（取行剩余部分），
                // 原代码用 StringTokenizer 取 3 个词，值里有空格会被截断
                String str = "" + ObjInfo[i];
                int a = str.indexOf(' ');
                int b = (a < 0) ? -1 : str.indexOf(' ', a + 1);
                if(b < 0) continue; // 跳过格式不完整的行
                infoList.add(new Info(str.substring(0, a),
                                      str.substring(a + 1, b),
                                      str.substring(b + 1)));
            }
        } catch(Exception e) {
            errorInFiles(e);
        } 
    }
    private void appendToUserFile(String str) {
        appendToFile(userFile, str);
    }
    private void appendToInfoFile(String str) {
        appendToFile(infoFile, str);
    }
    private void appendToFile(File e, String str)  {
        try {
            // 用 UTF-8 Writer 写入。原代码 writeBytes 只写每个字符的低 8 位，
            // 个人信息中的中文会被截断（与网络传输的中文问题同根源）
            FileOutputStream fileOut = new FileOutputStream(e,true);
            OutputStreamWriter osw = new OutputStreamWriter(fileOut, "UTF-8");
            BufferedWriter dataOut = new BufferedWriter(osw);
            dataOut.write(str + "\n");
            dataOut.close();
            osw.close();
            fileOut.close();
        } catch(IOException ioe) {
            System.out.println("FileDataSource alot of checks was done when initing " + 
                " " + e + " still it got messed up! This time no checks!");
            ioe.printStackTrace();
        }
    }
    private void removeUserFromFile(String user) {
        try {
            if(allWrite) {
                // 直接覆盖重写 users 文件。原代码写临时文件再 renameTo，
                // Windows 上 renameTo 到已存在的文件会失败，删除操作不落盘
                FileOutputStream fo = new FileOutputStream(userFile);
                OutputStreamWriter osw = new OutputStreamWriter(fo, "UTF-8");
                BufferedWriter w = new BufferedWriter(osw);
                for(int i=0;i<userComments.length;i++)
                    w.write(userComments[i] + "\n");
                Iterator it = users.iterator();
                while(it.hasNext()) {
                    User s = (User)it.next();
                    w.write("" + s.getName() + " " + s.getPass() + "\n");
                }
                w.close();
            }
        } catch(IOException ioe ) {
            System.out.println("FileDataSource.removeUserFromFile user " + user);
            ioe.printStackTrace();
        }
    }
    private void removeInfoFromFile(String user, String field, String data) {
    }
    private void errorInFiles(Exception e)  {
        System.out.println("FileDataSoure errorInFiles ");
        e.printStackTrace();
    }
    private Object[] readLines(File f) {
        LinkedList list = new LinkedList();
        try {
            FileInputStream fileIn = new FileInputStream(f);
            InputStreamReader isr = new InputStreamReader(fileIn, "UTF-8"); // 与写入编码一致
            BufferedReader dataIn = new BufferedReader(isr);
            String str = "";
            // 空行防护：原代码对空行 charAt(0) 抛越界异常，且被 finally 里的 return
            // 静默吞掉（Java 的 finally-return 会抑制异常），空行之后的用户全部丢失
            while( (str = dataIn.readLine()) != null )
                if(str.length() > 0 && str.charAt(0) != '#')
                    list.add(str);
            dataIn.close();
        } catch(IOException ioe) {
            System.out.println("FileDataSource.readLines(File " + f + "): " + ioe.getMessage());
        }
        return list.toArray();
    }
    public String getMD5(String str) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
            byte[] by = str.getBytes("UTF-8");
            md.update(by);
        } catch(Exception e) {e.printStackTrace();}
        byte[] b = md.digest();
        String strOut = "";
        for(int i=0;i<b.length;i++) strOut += b[i];
        return strOut;
    }
    // ===== 个人信息存取：内存 infoList + 落盘 info 文件，(用户,字段) 唯一，重复设置即覆盖 =====
    public boolean addInfo(String user, String field, String data) {
        if(user == null || field == null || data == null
            || field.length() == 0 || data.length() == 0) return false;
        removeFromList(user, field);
        infoList.add(new Info(user, field, data));
        rewriteInfoFile();
        return true;
    }
    public String getInfo(String user, String field) {
        Iterator it = infoList.iterator();
        while(it.hasNext()) {
            Info i = (Info)(it.next());
            if(i.user.equalsIgnoreCase(user) && i.field.equalsIgnoreCase(field))
                return i.data;
        }
        return null;
    }
    private boolean removeFromList(String user, String field) {
        boolean removed = false;
        Iterator it = infoList.iterator();
        while(it.hasNext()) {
            Info i = (Info)(it.next());
            if(i.user.equalsIgnoreCase(user) && i.field.equalsIgnoreCase(field)) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }
    // 全量重写 info 文件。直接覆盖写入，不走"临时文件+renameTo"
    // （Windows 上 renameTo 到已存在的文件会失败）
    private void rewriteInfoFile() {
        if(!allWrite) return;
        try {
            FileOutputStream fo = new FileOutputStream(infoFile);
            OutputStreamWriter osw = new OutputStreamWriter(fo, "UTF-8");
            BufferedWriter w = new BufferedWriter(osw);
            for(int i=0;i<infoComments.length;i++)
                w.write(infoComments[i] + "\n");
            Iterator it = infoList.iterator();
            while(it.hasNext()) {
                Info inf = (Info)(it.next());
                w.write(inf.user + " " + inf.field + " " + inf.data + "\n");
            }
            w.close();
        } catch(IOException ioe) {
            System.out.println("FileDataSource.rewriteInfoFile: " + ioe.getMessage());
        }
    }
    public boolean addUser(String user, String pass) {
        if(user == null || pass==null 
            || user.equalsIgnoreCase("") || pass.equalsIgnoreCase("") )
            return false;
        if(verifyUser(user,"")) {
            String md5 = getMD5(pass);
            User tmpUser = new User(user, md5);
            users.add(tmpUser);
            appendToUserFile(user + " " + md5);
            userListChanged = true;
            return true;
        }
        return false;
    }
    public boolean removeUser(String user, String pass) {
        if(user == null || pass==null 
            || user.equalsIgnoreCase("") || pass.equalsIgnoreCase("") )
            return false;
        Iterator it = users.iterator();
        boolean found = false;
        User s = null;
        while(it.hasNext() && !found) {
            s = (User)(it.next());
            if(s.getName().equalsIgnoreCase(user)) {
                found = true;
                if(s.getPass().equalsIgnoreCase(pass)) {
                    users.remove(s);
                    removeUserFromFile(user);
                    return (userListChanged = true);
                }                
            }
        }
        return false;
    }
    public boolean verifyUser(String user, String pass) {
        Iterator it = users.iterator();
        boolean found = false;
        User s = null;
        while(!found && it.hasNext()) {
            s = (User)(it.next());
            if(s.getName().equalsIgnoreCase(user)) {
                found = true;
            }
        }
        if(found) {
            if(s.getPass().equalsIgnoreCase(pass))
                return true;
            else return false;
        }
        return true;
    }
    class User {
        private String n;
        private String p;
        public User(String name, String password) {
            n = name;
            p = password;
        }
        public String getName() {
            return n;
        }
        public String getPass() {
            return p;
        }
        public void printUser() {
            System.out.println("FileDataSource.User registered user " + n);
        }
    }
    class Info {
        String user;
        String field;
        String data;
        public Info(String user, String field, String data) {
            this.user = user;
            this.field = field;
            this.data = data;
        }
        public void printInfo() {
            System.out.println("FileDataSource.Info registered info " + 
                               " \n\tuser " + user + " field " + field + " data " + data);
        }
    }
    public static void main(String args[]) {
        FileDataSource fd = new FileDataSource();
        fd.printAll();
        System.out.println("verify mihaly " + fd.verifyUser("mihaly", ""));
        System.out.println("addUser mihaly/thuglife " + fd.addUser("mihaly", "thuglife"));
        System.out.println("addUser outlaw/2Pac " + fd.addUser("outlaw", "2Pac"));
        System.out.println("verify mihaly/thuglife " + fd.verifyUser("mihaly", fd.getMD5("thuglife")));
        System.out.println("verify mihaly/Thuglife " + fd.verifyUser("mihaly", fd.getMD5("Thuglife")));
        System.out.println("verify outlaw/2Pac " + fd.verifyUser("outlaw", fd.getMD5("2Pac")));
        fd.printAll();
        System.out.println("remove mihaly/thuglife " + fd.removeUser("mihaly", fd.getMD5("thuglife")));
        fd.printAll();
    }
    public String[] getUserList() {
        int usSize = users.size();
        if(userListChanged && usSize>0) {
            us = new String[usSize];
            Iterator it = users.iterator();
            for(int i=0;it.hasNext();i++) 
                us[i] = "" + ((User)(it.next())).getName();
            userListChanged = false;
        }
        return us;
    }
    public String[] getAllUserInfo(String user) {
        LinkedList out = new LinkedList();
        Iterator it = infoList.iterator();
        while(it.hasNext()) {
            Info i = (Info)(it.next());
            // friends 是好友功能的保留字段，不作为个人信息展示
            if(i.user.equalsIgnoreCase(user) && !i.field.equalsIgnoreCase("friends"))
                out.add(i.field + ": " + i.data);
        }
        return (String[])out.toArray(new String[0]);
    }
    public boolean removeInfo(String user, String field, String info) {
        boolean removed = removeFromList(user, field);
        if(removed) rewriteInfoFile();
        return removed;
    }
}
