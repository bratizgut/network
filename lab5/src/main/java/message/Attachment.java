package message;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class Attachment {

    public enum Role {
        CLIENT, DNS_RESOLVER
    }

    public enum Status {
        GREETING, CONNECTION, CONNECTED, ERROR
    }

    public static int BUF_SIZE = 8 * 1024;

    private final int clientId;
    private Role role;
    private Status status = null;
    private ByteBuffer in = null;
    private ByteBuffer out = null;
    private SelectionKey peerKey = null;
    private InetAddress requestAddr = null;

    public Attachment(int clientId, Role role) {
        this.clientId = clientId;
        this.role = role;
        status = Status.GREETING;
    }

    public InetAddress getRequestAddr() {
        return requestAddr;
    }

    public void setRequestAddr(InetAddress requestAddr) {
        this.requestAddr = requestAddr;
    }

    public int getClientId() {
        return clientId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public ByteBuffer getIn() {
        return in;
    }

    public void setIn(ByteBuffer in) {
        this.in = in;
    }

    public ByteBuffer getOut() {
        return out;
    }

    public void setOut(ByteBuffer out) {
        this.out = out;
    }

    public SelectionKey getPeerKey() {
        return peerKey;
    }

    public void setPeerKey(SelectionKey peer) {
        this.peerKey = peer;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
