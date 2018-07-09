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
import java.util.ArrayList;
import java.util.Iterator;

public class Worker extends Thread{
    private Thread t;
    private String threadName;
    private ArrayList<InetSocketAddress> list;
    private byte[] request;
    private int maxSocketCount = 30;

    private File file;
    private MappedByteBuffer fileBuffer;

    /*constructor*/
    public Worker(String name, ArrayList<InetSocketAddress> list,byte[] request){
        this.list = list;
        this.threadName = name;
        this.request = request.clone(); //so we dont have to serialise access
        this.file = new File(this.threadName+".txt");
        file.delete();
        try {
            FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();


            this.fileBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE,0,4096*8*8);
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
                        if (debug) System.out.println("is writable");
                        SocketChannel channel = (SocketChannel) key.channel();
                        channel.write(ByteBuffer.wrap(request));
                            /*prepare for reading responses*/
                        key.interestOps(SelectionKey.OP_READ);
                        if (debug) System.out.println("writing finished");

                    }
                    if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ByteBuffer out = ByteBuffer.allocate(8192);
                        out.clear();
                        int bytesRead = channel.read(out);
                            /*for now we just exit if a connection is closed (usually after timeout by peer)*/
                        if (bytesRead == -1) return;
                        if (debug) System.out.println("remaining = " + out.remaining());
                        if (debug) System.out.println("read = " + bytesRead);
                        String v = new String(out.array());
                        /*is ok calling a method from main (another thread?) -> no, this method uses variables that are not(!) serialised
                        but would be good to deligate that work to other treads so we dont have to wait for the finished inspection every time*/
                        if(extractCertificate(out.array())){ //if this returns true, a certificate was found, so we don't need to watch the socket anymore
                            key.cancel();
                            /*this finishes the connection in a friendly matter...*/
                            channel.shutdownOutput();
                            //channel.close();
                            closedSockets++;
                            continue;
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
    public boolean extractCertificate(byte[] handshake){
        /*search for certificate start, assuming certificate is the first handshake type in the message (which is standard)*/
        for(int i = 0; i < handshake.length;i++){
            /*beware out of bounds*/
            if(handshake[i]==(byte)22 && i < handshake.length-5) {
                if(!(handshake[i+1] == (byte)3 && handshake[i+2] == (byte)3))continue;
                System.out.println("handshake message start found");
                if(handshake[i+5] == (byte)0x0b){//found certificate start

                    System.out.println("cert start found");
                    /*write certificate to memory mapped file (different one for each thread to avoid serialisation)*/
                    for(int j = i+6;j < handshake.length; j++){
                        this.fileBuffer.put(handshake[j]);
                    }
                    return true;
                }
                /*in these cases, we can skip examining the next (length) bytes (length in message)*/
                else if(handshake[i+5] == (byte)0x0c){
                    System.out.println("found key exchange start");
                }
                else if(handshake[i+5] == (byte)0x02){
                    System.out.println("found server hello");
                }
                else System.out.println("byte = "+handshake[i+5]); //probably server hello done identifier

            }
        }
        return false;
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
