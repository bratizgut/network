package proxy;

import message.Attachment;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DnsResolver {
    private final Attachment dnsAttachment;
    private final SelectionKey dnsKey;
    private final int MESSAGE_LENGTH = 1024;
    private final DatagramChannel datagramChannel;
    private final List<InetSocketAddress> dnsServers;
    private final Map<Integer, SelectionKey> domainNameClientMap = new HashMap<>();

    public DnsResolver(Selector selector) throws IOException {
        dnsServers = ResolverConfig.getCurrentConfig().servers();
        this.datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        Attachment dnsResolverAttachment = new Attachment(0, Attachment.Role.DNS_RESOLVER);
        dnsResolverAttachment.setStatus(null);
        dnsAttachment = dnsResolverAttachment;
        dnsKey = datagramChannel.register(selector, SelectionKey.OP_READ, dnsResolverAttachment);
    }

    public void readDnsMessage(SelectionKey key) throws IOException {
        DatagramChannel channel = ((DatagramChannel) key.channel());
        Attachment attachment = (Attachment) key.attachment();
        if (attachment.getIn() == null) {
            attachment.setIn(ByteBuffer.allocate(MESSAGE_LENGTH));
        } else {
            attachment.getIn().clear();
        }
        if (channel.receive(attachment.getIn()) == null) {
            return;
        }

        Message dnsMessage = parseDnsMessage(attachment.getIn().array());
        int id = dnsMessage.getHeader().getID();

        List<org.xbill.DNS.Record> answers = dnsMessage.getSection(id);
        ARecord answer = null;

        for (org.xbill.DNS.Record record : answers) {
            if (record.getType() == Type.A) {
                answer = (ARecord) record;
                break;
            }
        }

        if (answer != null) {
            setResolvedAddress(answer, id);
        }
    }

    private void setResolvedAddress(ARecord answer, int clientId) {
        Attachment clientAttachment = (Attachment) domainNameClientMap.get(clientId).attachment();
        clientAttachment.setRequestAddr(answer.getAddress());
        domainNameClientMap.get(clientId).interestOps(SelectionKey.OP_WRITE);
        domainNameClientMap.remove(clientId);
    }

    public Message parseDnsMessage(byte[] dnsMessage) throws IOException {
        return new Message(dnsMessage);
    }

    private byte[] makeDnsMessage(String name, Integer clientId) throws TextParseException {
        Message dnsMessage = new Message(clientId);
        Header header = dnsMessage.getHeader();
        header.setOpcode(Opcode.QUERY);
        header.setFlag(Flags.RD);
        dnsMessage.addRecord(org.xbill.DNS.Record.newRecord(new Name(name), Type.A, DClass.IN), Section.QUESTION);
        return dnsMessage.toWire(MESSAGE_LENGTH);
    }

    public void makeDnsRequest(String name, Integer clientId, SelectionKey key) throws TextParseException {
        byte[] dnsMsg = makeDnsMessage(name, clientId);
        domainNameClientMap.put(clientId, key);

        ByteBuffer dnsBuf = dnsAttachment.getOut();
        if (dnsBuf != null) {
            dnsAttachment.setOut(ByteBuffer.allocate(dnsBuf.capacity() + dnsMsg.length).put(dnsBuf).put(dnsMsg));
        } else {
            dnsAttachment.setOut(ByteBuffer.wrap(dnsMsg));
        }

        dnsKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }


    public void sendDnsRequest(SelectionKey key) throws IOException {
        datagramChannel.send(dnsAttachment.getOut(), dnsServers.get(0));
        dnsAttachment.getOut().compact();
        if (dnsAttachment.getOut().position() == 0) {
            dnsAttachment.setOut(null);
            key.interestOps(SelectionKey.OP_READ);
        }
    }
}
