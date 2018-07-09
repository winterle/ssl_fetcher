import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
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

    /*constructor*/
    public Worker(String name, ArrayList<InetSocketAddress> list,byte[] request){
        this.list = list;
        this.threadName = name;
        this.request = request;
        System.out.println("created thread "+threadName);
    }

    /**@override run()
     * */
    public void run(){
        System.out.println("Thread "+ threadName + " running");
        boolean debug = false;
        /*program logic will be here*/
        int socketCount = this.list.size(); //for now
        try {
            Selector selector = Selector.open();
            for (int i = 0; i < socketCount; i++) {
                SocketChannel curr = SocketChannel.open();
                curr.configureBlocking(false);
                SelectionKey newKey = curr.register(selector, SelectionKey.OP_CONNECT);
                curr.connect(list.get(i));
            }
            while (true) {
                int readyChannels = selector.select();

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
                        //is ok calling a function from main (another thread?)
                        Main.extractCertificate(out.array());
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
