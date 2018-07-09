import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.*;


/*todo
* find way to continue reading data from the connection until the certificate is found -> very inefficient, fix
 * cancel the connection to the server instantaniously w/o FIN
* socket (other objects) reuse?
* multithreading? worker threads?
* datastruct for saving the certificate (just write to file?)
* */
public class Main {
        static boolean debug = false;
        static int startCount = 0;
        static int certCount = 0;
    public static void extractCertificate(byte[] handshake){
        //if(handshake[0]!=(byte)22)System.out.println("no handshake"+ handshake[0]);
        /*search for certificate start, assuming certificate is the first handshake type in the message (which is standard)*/
        for(int i = 0; i < handshake.length;i++){
            /*beware out of bounds*/
            if(handshake[i]==(byte)22 && i < handshake.length-5) {
                if(!(handshake[i+1] == (byte)3 && handshake[i+2] == (byte)3))continue;
                System.out.println("handshake message start found");
                if(handshake[i+5] == (byte)0x0b){//found certificate start
                    certCount++;
                    System.out.println("cert start found");
                    /*do something here*/
                }
                /*in these cases, we can skip examining the next (length) bytes (length in message)*/
                else if(handshake[i+5] == (byte)0x0c){
                    System.out.println("found key exchange start");
                    continue;
                }
                else if(handshake[i+5] == (byte)0x02){
                    System.out.println("found server hello");
                    continue;
                }
                else System.out.println("byte = "+handshake[i+5]); //probably server hello done identifier
                startCount++;
                break;
            }
        }
        System.out.println("startCount = "+startCount+"; certCount = "+certCount);
        //else System.out.println("ok");
        //for(int i = 0; i < handshake.length; i++)System.out.println(handshake[i]);
    }

    public static void client(byte[] request, ArrayList<InetSocketAddress> addressList){
        /*ByteBuffer containing the request to send to the servers -> remove this, no use (byte buffer is emptied on use(or the index becomes weird))*/
        ByteBuffer requestBuffer = ByteBuffer.allocate(request.length);
        requestBuffer.clear();
        requestBuffer.put(request);
        requestBuffer.flip();

        int socketCount = addressList.size();
        ArrayList<SelectionKey> keyList = new ArrayList<SelectionKey>();
        try {
            Selector selector = Selector.open();

        /*open a socket for each socketAdress inside the param addressList, also set them to non-blocking mode*/
        for(int i = 0; i < socketCount; i++){
                SocketChannel curr = SocketChannel.open();
                curr.configureBlocking(false);
                SelectionKey newKey = curr.register(selector, SelectionKey.OP_CONNECT);
                keyList.add(newKey);
                curr.connect(addressList.get(i));
        }

        /**/



                while (true) {
                    int readyChannels = selector.select();

                    Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                    while(keyIterator.hasNext()){
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();

                        if(!key.isValid())continue;

                        if(key.isConnectable()) {
                            SocketChannel channel = (SocketChannel) key.channel();
                            if(channel.isConnectionPending()){
                                channel.finishConnect();
                            }
                            channel.configureBlocking(false);
                            if(debug)System.out.println("connected");
                            channel.register(selector,SelectionKey.OP_WRITE);
                        }

                        if(key.isWritable()){
                            if(debug)System.out.println("is writable");
                            SocketChannel channel = (SocketChannel) key.channel();
                            channel.write(ByteBuffer.wrap(request));
                            /*prepare for reading responses*/
                            key.interestOps(SelectionKey.OP_READ);
                            if(debug)System.out.println("writing finished");

                        }
                        if(key.isReadable()){
                            SocketChannel channel = (SocketChannel) key.channel();
                            ByteBuffer out = ByteBuffer.allocate(8192);
                            out.clear();
                            int bytesRead = channel.read(out);
                            /*for now we just exit if a connection is closed (usually after timeout by peer)*/
                            if(bytesRead == -1)return;
                            if(debug)System.out.println("remaining = " + out.remaining());
                            if(debug)System.out.println("read = " + bytesRead);
                            String v = new String(out.array());
                            extractCertificate(out.array());
                            if(debug)System.out.println(v);
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    }



                }


        }
        catch (java.io.IOException e){
            e.printStackTrace();
        }

    }

    public static byte[] hexToByteArray(String hex){
        byte[] bytes = new byte[hex.length()/2];
        for(int i = 0; i < hex.length(); i+= 2){
            String sub = hex.substring(i,i+2);
            bytes[i/2] = (byte)Integer.parseInt(sub,16);
        }
        return bytes;
    }

    public static void main(String[] args) {
        String requestHex = "160301012c01000128030322dd79ffb657d1782dcf7298d1c98e21cd01c5d08ec7573fb61a2594b7ec45dc0000aac030c02cc028c024c014c00a00a500a300a1009f006b006a0069006800390038003700360088008700860085c032c02ec02ac026c00fc005009d003d00350084c02fc02bc027c023c013c00900a400a200a0009e00670040003f003e0033003200310030009a0099009800970045004400430042c031c02dc029c025c00ec004009c003c002f00960041c011c007c00cc00200050004c012c008001600130010000dc00dc003000a00ff01000055000b000403000102000a001c001a00170019001c001b0018001a0016000e000d000b000c0009000a00230000000d0020001e060106020603050105020503040104020403030103020303020102020203000f000101";
        /*tls1.0 for simplicity? not supported standard*/
        //requestHex = "16030100c0010000bc030132c342d62e5616e6c52910d675a6ac6918e55ffb6e45fe667d34fc366b4eddc7000062c014c00a00390038003700360088008700860085c00fc00500350084c013c0090033003200310030009a0099009800970045004400430042c00ec004002f00960041c011c007c00cc00200050004c012c008001600130010000dc00dc003000a00ff01000031000b000403000102000a001c001a00170019001c001b0018001a0016000e000d000b000c0009000a00230000000f000101";
        byte[] requestBytes = hexToByteArray(requestHex);

        /*provisional list of internet addresses (since hostnames, DNS before TCP connection)*/
        InetSocketAddress addr1 = new InetSocketAddress("google.com",443);
        InetSocketAddress addr2 = new InetSocketAddress("sar.informatik.hu-berlin.de",443);
        InetSocketAddress addr3 = new InetSocketAddress("google.de",443);
        InetSocketAddress addr4 = new InetSocketAddress("moodle.hu-berlin.de",443);
        InetSocketAddress addr5 = new InetSocketAddress("duckduckgo.com",443);


        ArrayList<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();

        addresses.add(addr1);
        addresses.add(addr2);
        addresses.add(addr3);
        addresses.add(addr4);
        addresses.add(addr5);
        addresses.add(new InetSocketAddress("github.com",443));
        addresses.add(new InetSocketAddress("reddit.com",443));
        addresses.add(new InetSocketAddress("tutorialspoint.com",443));
        addresses.add(new InetSocketAddress("netzpolitik.org",443));
        addresses.add(new InetSocketAddress("stackoverflow.com",443));



        client(requestBytes,addresses);

/*

        try {
            SocketChannel sc = SocketChannel.open();

            InetSocketAddress addr = new InetSocketAddress("sar.informatik.hu-berlin.de",443);
            sc.connect(addr);

            ByteBuffer buf = ByteBuffer.allocate(5000);
            buf.clear();
            buf.put(bytes);
            buf.flip();
            sc.write(buf);

            ByteBuffer out = ByteBuffer.allocate(2048);
            out.clear();
            int bytesRead = sc.read(out);
            System.out.println("remaining = " + out.remaining());
            System.out.println("read = " + bytesRead);
            String v = new String(out.array());
            System.out.println(v);

        }
        catch (java.io.IOException e){
            e.printStackTrace();
        }

*/
    }
}
