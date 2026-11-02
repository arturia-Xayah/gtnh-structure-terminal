import dev.aestoragevault.core.*;
import java.nio.file.*;
import java.math.BigInteger;
import java.util.*;
import java.lang.management.ManagementFactory;

public class ReadViewAudit {
    static volatile Object sink;
    static final com.sun.management.ThreadMXBean ALLOC = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static Resource resource(int id) {
        return new Resource("items",8,new byte[]{(byte)id,(byte)(id>>>8),(byte)(id>>>16),(byte)(id>>>24)},new byte[32]);
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);Files.createDirectories(root);
        System.out.println("types,mode,ns_per_snapshot,allocated_bytes_per_snapshot");
        for(int n:new int[]{1000,10000,100000}) {
            try(VaultStore s=new VaultStore(root.resolve("n"+n),UUID.randomUUID(),true)) {
                Map<Resource,BigInteger> seed=new HashMap<>();
                for(int i=0;i<n;i++)seed.put(resource(i),BigInteger.valueOf(1000));
                s.absorbExact(UUID.randomUUID(),BigInteger.valueOf(n).multiply(BigInteger.valueOf(100000)),seed);
                s.nativeSnapshot("items");
                for(int mode=0;mode<3;mode++) {
                    for(int i=0;i<100;i++)run(s,n,mode,i);
                    long allocated=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId());
                    long t=System.nanoTime();
                    for(int i=0;i<200;i++)run(s,n,mode,i);
                    long elapsed=System.nanoTime()-t;
                    long bytes=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId())-allocated;
                    System.out.println(n+","+new String[]{"unchanged","quantity_changes","membership_churn"}[mode]+","+(elapsed/200.0)+","+(bytes/200.0));
                    if(s.types()!=n||s.exactAmount(resource(0)).longValue()!=1000)throw new AssertionError("stock changed");
                }
            }
        }
    }
    static void run(VaultStore s,int n,int mode,int i)throws Exception {
        Resource r=resource(mode==2?n:i%n);
        if(mode!=0){
            if(s.insert(r,1,false)!=1||s.extract(r,1,false)!=1)throw new AssertionError("IO");
        }
        NativeInventoryView.Snapshot view=s.nativeSnapshot("items");
        if(view.size()!=n)throw new AssertionError("snapshot size");
        sink=view;
    }
}
