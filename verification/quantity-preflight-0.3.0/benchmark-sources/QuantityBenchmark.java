import dev.aestoragevault.core.Resource;
import dev.aestoragevault.core.VaultStore;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Isolated actual ledger, not a synthetic BigInteger.add loop or a server TPS claim. */
public final class QuantityBenchmark {
    static volatile long sink;
    static final com.sun.management.ThreadMXBean ALLOC =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long allocated() { return ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId()); }
    static long heap() throws Exception { System.gc(); Thread.sleep(80); System.gc(); Thread.sleep(80);
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
    static double median(double[] a) { Arrays.sort(a); return a[a.length/2]; }
    public static void main(String[] args) throws Exception {
        Path dir=Paths.get(args[0]); int types=Integer.parseInt(args[1]),bits=Integer.parseInt(args[2]);
        List<Resource> resources=new ArrayList<>();
        for(int i=0;i<types;i++) { byte[] key=new byte[16],payload=new byte[96];
            for(int k=0;k<4;k++) key[k]=(byte)(i >>>(8*k));
            resources.add(new Resource(i%2==0?"items":"fluids",i%2==0?8:2048,key,payload)); }
        BigInteger initial=BigInteger.ONE.shiftLeft(bits-1); UUID id=UUID.randomUUID();
        long emptyHeap=heap(); VaultStore store=new VaultStore(dir,id,true);
        store.absorb(UUID.randomUUID(), initial.multiply(BigInteger.valueOf(types)),Collections.emptyMap());
        if(bits<=63) {Map<Resource,Long> stock=new HashMap<>();for(int i=0;i<resources.size();i++)stock.put(resources.get(i),initial.longValue()+i);
            store.absorb(UUID.randomUUID(),BigInteger.ONE,stock);
        } else { java.lang.reflect.Method seed=VaultStore.class.getMethod("seedExact",Resource.class,BigInteger.class);
            for(int i=0;i<resources.size();i++)seed.invoke(store,resources.get(i),initial.add(BigInteger.valueOf(i))); }
        store.checkpoint(); long populatedHeap=heap();
        for(int i=0;i<4;i++) simulate(store,resources,100000);
        modulate(store,resources,1000);
        double[] ns=new double[5],alloc=new double[5],wal=new double[3],wa=new double[3],enumerate=new double[5];
        for(int i=0;i<5;i++) {long a=allocated(),t=System.nanoTime();simulate(store,resources,200000);
            ns[i]=(System.nanoTime()-t)/400000.0;alloc[i]=(allocated()-a)/400000.0;}
        for(int i=0;i<3;i++){long a=allocated(),t=System.nanoTime();modulate(store,resources,2000);
            wal[i]=(System.nanoTime()-t)/4000.0;wa[i]=(allocated()-a)/4000.0;}
        for(int i=0;i<5;i++){long t=System.nanoTime();for(int j=0;j<10;j++) {Map<Resource,?> e=store.entries();long sum=0;
                for(Object v:e.values())sum+= v instanceof BigInteger?((BigInteger)v).min(BigInteger.valueOf(1L<<40)).longValue():Math.min(((Long)v),1L<<40);
                sink=sum;}
            enumerate[i]=(System.nanoTime()-t)/10.0;}
        long walBytes=Files.size(dir.resolve(id+".wal"));long t=System.nanoTime();store.checkpoint();
        double checkpointMs=(System.nanoTime()-t)/1e6;long snapshotBytes=Files.size(dir.resolve(id+".snapshot"));store.close();
        t=System.nanoTime();store=new VaultStore(dir,id,false);double loadMs=(System.nanoTime()-t)/1e6;
        if(store.types()!=types || store.amount(resources.get(0))!=(bits<=63?initial.longValue():Long.MAX_VALUE))throw new AssertionError("quantity changed");
        store.close();
        System.out.println("{\"types\":"+types+",\"quantity_bits\":"+bits+",\"simulate_ns_per_op\":"+median(ns)+
            ",\"simulate_alloc_bytes_per_op\":"+median(alloc)+",\"wal_ns_per_op\":"+median(wal)+
            ",\"wal_alloc_bytes_per_op\":"+median(wa)+",\"enumerate_ns\":"+median(enumerate)+
            ",\"retained_heap_delta\":"+(populatedHeap-emptyHeap)+",\"snapshot_bytes\":"+snapshotBytes+
            ",\"wal_bytes_14000_changes\":"+walBytes+",\"checkpoint_ms\":"+checkpointMs+",\"load_ms\":"+loadMs+"}");
    }
    static void simulate(VaultStore s,List<Resource> r,int n)throws Exception {long sum=0;int size=r.size();
        for(int i=0;i<n;i++){Resource x=r.get(i%size);sum+=s.insert(x,64,true);sum+=s.extract(x,64,true);}sink=sum;}
    static void modulate(VaultStore s,List<Resource> r,int n)throws Exception {long sum=0;int size=r.size();
        for(int i=0;i<n;i++){Resource x=r.get(i%size);sum+=s.insert(x,64,false);sum+=s.extract(x,64,false);if(i%100==99)s.flush();}s.flush();sink=sum;}
}
