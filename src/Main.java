import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;



/*todo
* find way to continue reading data from the connection until the certificate is found -> very inefficient, fix
 * cancel the connection to the server instantaniously w/o FIN
* socket (other objects) reuse?
* datastruct for saving the certificate (just write to file?)
* */
public class Main {
        static int certCount = 0;


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
        byte[] requestBytes = hexToByteArray(requestHex);

        /*provisional list of internet addresses (since hostnames, DNS before TCP connection)*/

        ArrayList<InetSocketAddress> addrList1 = new ArrayList<InetSocketAddress>();
        ArrayList<InetSocketAddress> addrList2 = new ArrayList<InetSocketAddress>();

        addrList1.add(new InetSocketAddress("google.com",443));
        addrList1.add(new InetSocketAddress("sar.informatik.hu-berlin.de",443));
        addrList1.add(new InetSocketAddress("google.de",443));
        addrList1.add(new InetSocketAddress("moodle.hu-berlin.de",443));
        addrList1.add(new InetSocketAddress("duckduckgo.com",443));
        addrList2.add(new InetSocketAddress("github.com",443));
        addrList2.add(new InetSocketAddress("reddit.com",443));
        addrList2.add(new InetSocketAddress("tutorialspoint.com",443));
        addrList2.add(new InetSocketAddress("netzpolitik.org",443));
        addrList2.add(new InetSocketAddress("stackoverflow.com",443));

        /*reading ip's from a text file for demonstration purposes*/
        try {
            BufferedReader br = new BufferedReader(new FileReader("ips.txt"));
            String line = br.readLine();
            while(line != null){
                addrList1.add(new InetSocketAddress(line,443));
                System.out.println(line);
                line = br.readLine();
            }
            br.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }


        Worker worker1 = new Worker("worker1",addrList1,requestBytes);
        Worker worker2 = new Worker("worker2",addrList2,requestBytes);
        worker1.start();
        worker2.start();
        try{
            worker1.join(10000);
            worker2.join(10000);
        }
        catch(InterruptedException e){
            System.out.println("interrupted");
        }
        if(worker1.isAlive()){
            System.out.println("worker1 is still alive, might have unfinished work");
        }
        if(worker2.isAlive()){
            System.out.println("worker2 is still alive, might have unfinished work");
        }
    }
}
