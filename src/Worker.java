import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class Worker extends Thread{
    private Thread t;
    private String threadName;
    private ArrayList<InetSocketAddress> list;
    private byte[] request;
    private int maxSocketCount = 30;
    private int writtenCount = 0;
    private String separatorS = "XXXXXXXXXX";//10 times X
    private byte[] separator = separatorS.getBytes();
    //private boolean a = true;

    private Path file;

    /*constructor*/
    public Worker(String name, ArrayList<InetSocketAddress> list,byte[] request){
        this.list = list;
        this.threadName = name;
        this.request = request.clone(); //so we dont have to serialise access
        try {
            this.file = Paths.get(this.threadName+".bin");

        }
        catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("created thread "+threadName);
    }

    /**@override run()
     * */
    public void run(){
        System.out.println("Thread "+ threadName + " running");
        boolean debug = false;
        /*actual program logic*/
        int socketCount = this.list.size(); //for now
        try {
            Selector selector = Selector.open();
            for (int i = 0; i < socketCount; i++) {
                SocketChannel curr = SocketChannel.open();
                curr.configureBlocking(false);
                SelectionKey newKey = curr.register(selector, SelectionKey.OP_CONNECT);
                curr.connect(list.get(i));
            }
            /*workaround for endless loop*/
            int closedSockets = 0;
            while (true) {
                int readyChannels;
                if(closedSockets < socketCount){readyChannels = selector.select();}
                else break;

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) continue;

                    if (key.isConnectable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        if (channel.isConnectionPending()) {
                            channel.finishConnect();
                        }
                        channel.configureBlocking(false);
                        if (debug) System.out.println("connected");
                        channel.register(selector, SelectionKey.OP_WRITE);
                    }

                    if (key.isWritable()) {

                        SocketChannel channel = (SocketChannel) key.channel();
                        channel.write(ByteBuffer.wrap(request));
                            /*prepare for reading responses*/
                        key.interestOps(SelectionKey.OP_READ);
                        ByteBuffer newBuf = ByteBuffer.allocate(2048*8*8);
                        key.attach(newBuf);

                    }
                    if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ByteBuffer buffer = (ByteBuffer)key.attachment();
                        int bytesRead = channel.read(buffer);
                            /*for now we just exit if a connection is closed (usually after timeout by peer)*/
                        if (bytesRead == -1) return;
                        if (debug) System.out.println("remaining = " + buffer.remaining());
                        if (debug) System.out.println("read = " + bytesRead);
                        String v = new String(buffer.array());
                        /*is ok calling a method from main (another thread?) -> no, this method uses variables that are not(!) serialised
                        but would be good to deligate that work to other treads so we dont have to wait for the finished inspection every time*/
                        int ret = extractCertificate(buffer.array(),buffer.position());
                        if(ret == 1){ //if this returns 1, a certificate was found, so we don't need to watch the socket anymore
                            key.cancel();
                            /*this finishes the connection in a friendly matter...*/
                            channel.shutdownOutput();
                            //channel.close();
                            closedSockets++;
                            continue;
                        }
                        else if(ret == 0){
                            buffer.clear();
                        }
                        else if(ret == 2){//continue buffering

                        }
                        if (debug) System.out.println(v);
                        key.interestOps(SelectionKey.OP_READ);
                    }
                }
            }
        }

        catch(Exception e){
            e.printStackTrace();
            System.out.println("Exception in thread "+threadName+", exiting thread...");
        }
    }
    public int extractCertificate(byte[] handshake,int limit){
        /*search for certificate start, assuming certificate is the first handshake type in the message (which is standard)*/
        for(int i = 0; i < limit;i++){
            /*beware out of bounds*/
            if(handshake[i]==(byte)0x16 && i < limit-5) {
                i+=5;
                /*handshake type identifier*/
                byte id = handshake[i];
                if(id == (byte)0x0b){
                    i+=4;
                    if(i >= limit)return 2; //continue buffering
                    System.out.println("cert start found");
                    /*determine the total length of the certificates*/
                    int totalLen = 0;
                    System.out.println(Byte.toString(handshake[i]) + "   " + Byte.toString(handshake[i+1]) + "    " + Byte.toString(handshake[i+2]));
                    totalLen+= (handshake[i]&0xFF);//java likes signed bytes
                    totalLen=totalLen<<8;
                    totalLen+=(handshake[i+1]&0xFF);//java likes signed bytes
                    totalLen=totalLen<<8;
                    totalLen+=(handshake[i+2]&0xFF);//java likes signed bytes
                    System.out.println("totalLen ="+totalLen+"limit = " +limit);
                    if(totalLen > limit-i+2) {
                        System.out.println("not done");
                        return 2;
                    }
                    else {
                        i+=3;
                        System.out.println("finally captured all necessary packets, beginning to extract certificates...");
                        int certLen;
                        int written = 0;
                        do{
                            certLen = 0;
                            certLen+= (handshake[i]&0xFF);//java likes signed bytes
                            certLen=certLen<<8;
                            certLen+=(handshake[i+1]&0xFF);//java likes signed bytes
                            certLen=certLen<<8;
                            certLen+=(handshake[i+2]&0xFF);//java likes signed bytes
                            //System.out.println("this cert len = "+ certLen);
                            this.writtenCount++;
                            //if(!a)return 1;
                            System.out.println("writing to file... count = "+writtenCount);
                            try {
                                //Files.write(file, handshake, StandardOpenOption.APPEND);
                                String s;// = new String(handshake);
                                //System.out.println(" I = " + i);
                                //s = s.substring(i+3,i+3+certLen);
                                if(certLen > 10000)return 1;

                                byte[] slize = Arrays.copyOfRange(handshake,i+3,i+3+certLen);
                                Files.write(file, slize, StandardOpenOption.APPEND);
                                System.out.println(slize.length);
                                s = new String(slize);
                                System.out.println(s);

                                //System.out.println("lenght = "+s.length());
                                Files.write(file,separator,StandardOpenOption.APPEND);
                                //a = false;
                            }
                            catch(Exception e){
                                e.printStackTrace();
                            }
                            written+=certLen;
                            i+=certLen+3;
                        }while(written < totalLen);
                        return 1;
                    }
                }
                if(id == (byte)0x0c){
                    System.out.println("found key exchange start");
                }
                else if(id == (byte)0x02){
                    System.out.println("found server hello");
                }
                else System.out.println("byte = "+id); //probably server hello done identifier
            }
        }
        return 0;
    }
    /**@override start()
     */
    public void start(){
        System.out.println("starting thread "+threadName);
        if(t == null){
            t = new Thread(this,threadName);
            t.start();
        }
    }
}
