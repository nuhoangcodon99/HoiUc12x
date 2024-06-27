package server;

import assembly.Player;
import io.IMessageHandler;
import io.Message;
import io.Util;
import stream.Client;
import stream.Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

public class Session {

    private static int baseId;
    private static final String KEY = "D";
    public int id;
    public long outdelay = 20L;
    private boolean connected = false;
    private boolean getKeyComplete = false;
    private byte curR;
    private byte curW;
    public Socket socket;
    protected DataInputStream dis;
    protected DataOutputStream dos;
    IMessageHandler messageHandler;
    private Object LOCK = new Object();
    public Player player = null;
    byte type;
    public byte zoomLevel;
    private boolean isGPS;
    private int width;
    private int height;
    private boolean isQwert;
    private boolean isTouch;
    private String plastfrom;
    private byte languageId;
    private int provider;
    private String agent;
    public int version;
    private Server server = Server.gI();
    public String ipv4 = null;
    public boolean login;
    public int idSer;
    private Session.Sender sender;
    private Thread collectorThread;
    protected Thread sendThread;

    public InetSocketAddress socketAddress;
    public String clientIpAddress;

    public Session(Socket socket, IMessageHandler handler) {
        try {
            this.id = baseId++;
            this.socket = socket;
            this.dis = new DataInputStream(this.socket.getInputStream());
            this.dos = new DataOutputStream(this.socket.getOutputStream());
            this.connected = true;
            this.messageHandler = handler;
            this.sendThread = new Thread(this.sender = new Session.Sender());
            this.collectorThread = new Thread(new Session.MessageCollector());
            this.socketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
            this.clientIpAddress = socketAddress.getAddress().getHostAddress();
        } catch (Exception e) {
            System.err.println("ERROR NEW SESSION");
            e.printStackTrace();
        }
    }

    public void run() {
        this.sendThread.start();
        this.collectorThread.start();
    }

    public boolean isConnected() {
        return this.connected;
    }

    public void sendMessage(Message message) {
        if (this.isConnected()) {
            this.sender.addMessage(message);
        }
    }

    protected void doSendMessage(final Message m) throws IOException {
        try {
            final byte[] data = m.getData();
            if (data != null) {
                byte b = m.getCommand();
                final int size = data.length;
                if (size > 65535) {
                    b = -32;
                }
                if (this.getKeyComplete) {
                    this.dos.writeByte(this.writeKey(b));
                } else {
                    this.dos.writeByte(b);
                }
                if (b == -32) {
                    b = m.getCommand();
                    if (this.getKeyComplete) {
                        this.dos.writeByte(this.writeKey(b));
                    } else {
                        this.dos.writeByte(b);
                    }
                    final int byte1 = this.writeKey((byte) (size >> 24));
                    this.dos.writeByte(byte1);
                    final int byte2 = this.writeKey((byte) (size >> 16));
                    this.dos.writeByte(byte2);
                    final int byte3 = this.writeKey((byte) (size >> 8));
                    this.dos.writeByte(byte3);
                    final int byte4 = this.writeKey((byte) (size & 0xFF));
                    this.dos.writeByte(byte4);
                } else if (this.getKeyComplete) {
                    final int byte1 = this.writeKey((byte) (size >> 8));
                    this.dos.writeByte(byte1);
                    final int byte2 = this.writeKey((byte) (size & 0xFF));
                    this.dos.writeByte(byte2);
                } else {
                    final int byte1 = (byte) (size & 0xFF00);
                    this.dos.writeByte(byte1);
                    final int byte2 = (byte) (size & 0xFF);
                    this.dos.writeByte(byte2);
                }
                if (this.getKeyComplete) {
                    for (int i = 0; i < size; ++i) {
                        data[i] = this.writeKey(data[i]);
                    }
                }
                this.dos.write(data);
//                util.Debug("do mss " + b + " size " + size);
            }
            this.dos.flush();
        } catch (IOException e) {
            this.closeMessage();
            Util.Debug("Error write message from client " + this.id);
        }
    }

    private byte readKey(byte b) {
        byte[] bytes = KEY.getBytes();
        byte curR = this.curR;
        this.curR = (byte) (curR + 1);
        byte i = (byte) (bytes[curR] & 255 ^ b & 255);
        if (this.curR >= KEY.getBytes().length) {
            this.curR %= (byte) KEY.getBytes().length;
        }
        return i;
    }

    private byte writeKey(byte b) {
        byte[] bytes = KEY.getBytes();
        byte curW = this.curW;
        this.curW = (byte) (curW + 1);
        byte i = (byte) (bytes[curW] & 255 ^ b & 255);
        if (this.curW >= KEY.getBytes().length) {
            this.curW %= (byte) KEY.getBytes().length;
        }
        return i;
    }

    public void hansakeMessage() {
        Message m = null;
        try {
            m = new Message(-27);
            m.writer().writeByte(KEY.getBytes().length);
            m.writer().writeByte(KEY.getBytes()[0]);
            for (int i = 1; i < KEY.getBytes().length; ++i) {
                m.writer().writeByte(KEY.getBytes()[i] ^ KEY.getBytes()[i - 1]);
            }
            m.writer().flush();
            this.doSendMessage(m);
            this.getKeyComplete = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (m != null) {
                m.cleanup();
            }
        }
    }

    public void setConnect(Message m) {
        try {
            this.type = m.reader().readByte();
            this.zoomLevel = m.reader().readByte();
            this.isGPS = m.reader().readBoolean();
            this.width = m.reader().readInt();
            this.height = m.reader().readInt();
            this.isQwert = m.reader().readBoolean();
            this.isTouch = m.reader().readBoolean();
            this.plastfrom = m.reader().readUTF();
            m.reader().readInt();
            m.reader().readByte();
            this.languageId = m.reader().readByte();
            this.provider = m.reader().readInt();
            this.agent = m.reader().readUTF();
            m.cleanup();
            Util.Debug("Connection type " + this.type + " zoomlevel " + this.zoomLevel + " width " + this.width + " height " + this.height);
        } catch (Exception var3) {
            this.closeMessage();
        } finally {
            if (m != null) {
                m.cleanup();
            }
        }
    }

    public void loginGame(Message m) {
        try {
            if (this.login) {
                Service.ClearCache(this);
                return;
            }
            String uname = Util.strSQL(m.reader().readUTF());
            String passw = Util.strSQL(m.reader().readUTF());
            String vs = m.reader().readUTF();
            final String t1 = m.reader().readUTF();
            final String packages = m.reader().readUTF();
            final String random = m.reader().readUTF();
            final byte sv = m.reader().readByte();
            this.version = Integer.parseInt(vs.replace(".", ""));
            m.cleanup();
            Player p = Player.login(this, uname, passw);
            if (p != null) {
                this.player = p;
                this.outdelay = 0L;
                this.login = true;
                Manager.getPackMessage(p);
                System.out.println("- Tài Khoản: " + p.username + " - IP : " + this.clientIpAddress);
            } else {
                this.login = false;
                if (Client.timeWaitLogin.containsKey(uname) && Client.timeWaitLogin.get(uname) - System.currentTimeMillis() > 20000L) {
                    Thread.sleep(1000L);
                    this.closeMessage();
                }
            }
        } catch (Exception var10) {
            var10.printStackTrace();
            this.closeMessage();
        } finally {
            if (m != null) {
                m.cleanup();
            }
        }
    }
// sửa

    public void disconnect() {
        if (this.connected) {
            this.connected = false;
            // sửa
            this.login = false;
            try {
                if (this.socket != null) {
                    this.socket.close();
                }
                if (dis != null) {
                    dis.close();
                    dis = null;
                }
                if (dos != null) {
                    dos.close();
                    dos = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.messageHandler.onDisconnected(this);
            this.sendThread = null;
            this.collectorThread = null;
        }

    }

    public void closeMessage() {
        if (this.connected) {
            try {
                Client.gI().kickSession(this);
            } catch (Exception var2) {
                var2.printStackTrace();
            }
        }
    }

    public String toString() {
        return "Conn:" + this.id;
    }

    public void sendMessageLog(String str) {
        Message m = null;
        try {
            m = new Message(-26);
            m.writer().writeUTF(str);
            m.writer().flush();
            this.sendMessage(m);
        } catch (Exception var3) {
            var3.printStackTrace();
        } finally {
            if (m != null) {
                m.cleanup();
            }
        }

    }

    class MessageCollector implements Runnable {

        @Override
        public void run() {
            Message message = null;
            while (true) {
                try {
                    if (Session.this.connected && Session.this.dis != null) {
                        message = this.readMessage();
                        if (message != null && Session.this != null) {
                            if (!login && !(message.getCommand() == -27 || message.getCommand() == -29)) {
                                socket.close();
                            } else {
                                Util.Debug("Session: " + Session.this.id + " do message " + message.getCommand() + " size " + message.reader().available());
                                Session.this.messageHandler.processMessage(Session.this, message);
                                message.cleanup();
                                continue;
                            }
                        }
                    }
                } catch (Exception var2) {
                    var2.printStackTrace();
                } finally {
                    if (message != null) {
                        message.cleanup();
                    }
                }
                Session.this.closeMessage();
                Session.this.dis = null;
                return;
            }
        }

        private Message readMessage() {
            try {
                byte cmd = Session.this.dis.readByte();
                if (Session.this.getKeyComplete) {
                    cmd = Session.this.readKey(cmd);
                }
                int size;
                if (cmd == -31) {
                    size = Session.this.dis.readShort();
                } else if (Session.this.getKeyComplete) {
                    final byte b1 = Session.this.dis.readByte();
                    final byte b2 = Session.this.dis.readByte();
                    size = ((Session.this.readKey(b1) & 0xFF) << 8 | (Session.this.readKey(b2) & 0xFF));
                } else {
                    size = Session.this.dis.readUnsignedShort();
                }
                final byte[] data = new byte[size];
                for (int len = 0, byteRead = 0; len != -1 && byteRead < size; byteRead += len) {
                    len = Session.this.dis.read(data, byteRead, size - byteRead);
                    if (len > 0) {
                    }
                }
                if (Session.this.getKeyComplete) {
                    for (int i = 0; i < data.length; ++i) {
                        data[i] = Session.this.readKey(data[i]);
                    }
                }
                final Message msg = new Message(cmd, data);
                return msg;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private class Sender implements Runnable {

        private ArrayList<Message> sendingMessage;

        private Sender() {
            this.sendingMessage = new ArrayList();
        }

        public void addMessage(Message message) {
            if (Session.this.isConnected()) {
                this.sendingMessage.add(message);
            }
        }

        @Override
        public void run() {
            while (true) {
                try {
                    if (Session.this.connected && Session.this.dis != null) {
                        while (this.sendingMessage != null && this.sendingMessage.size() > 0 && Session.this.dos != null) {
                            Message m = this.sendingMessage.remove(0);
                            if (m != null) {
                                Session.this.doSendMessage(m);
                                m.cleanup();
                            }
                        }
                        Thread.sleep(10L);
                        continue;
                    }
                } catch (Exception var2) {
                    var2.printStackTrace();
                }
                try {
                    this.sendingMessage.removeAll(this.sendingMessage);
                    this.sendingMessage = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }
        }
    }
}
