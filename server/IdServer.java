package server;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.Jedis;

/*
The server maintains a data store of login name to UUID mappings in memory. For each
newly created login name, the server creates a UUID (use the java.util.UUID class for
this purpose) and stores it in the mappings data store. 

For each login name, we also want
to store the IP address from where the request was sent, the date and time when the request
was received and the real user name associated with the login name, and the last change date
for each id
*/

public class IdServer implements Server {

    private Jedis jedis;
    private DataAccessObject dao;
    private boolean verbose;

    private final String defaultPassword = "defaultPassword";

    private int port;

    private static final int discoveryPort = 5000;
    private static final int timeout = 5000; // milliseconds
    private static final int default_timeout = 0; // infinite
    private static final String mcastAddress = "230.230.235.1";
    // eno1 for KLC computers, eth0 for WSL
    private static final String networkInterface = "eno1";

    private static final int DISCOVER_GROUP = 1;
    private static final int LEAVE_GROUP = 2;
    private static final int JOIN_GROUP = 3;
    private static final int I_AM_HERE = 4;
    private static final int HEARTBEAT = 5;

    private AtomicLong lTime = new AtomicLong(0);
    private long myServerId;
    private long currentCoordinatorId = -1;
    private boolean isCoordinator = false;
    private Thread heartbeatThread;
    private Timer heartbeatTimer;
    private ScheduledExecutorService heartbeatExecutor = null;
    private ScheduledExecutorService heartbeatListenerExecutor = null;
    private Set<String> acks = Collections.synchronizedSet(new HashSet<String>());

    private Set<String> servers = Collections.synchronizedSet(new HashSet<String>());
    private InetAddress addr;
    private SocketAddress group;
    private MulticastSocket s;
    private NetworkInterface net;

    private Object electionLock;
    private Object replicateLock;
    private String ip;
    private String coordinatorIP;
    private final int COORDINATOR_PORT = 5125;
    private boolean gotBullied = false;
    private boolean startedElection = false;

    private volatile long lastHeartBeat = System.currentTimeMillis();

    public IdServer(int port, boolean verbose, InetAddress addr, SocketAddress group, MulticastSocket s,
            NetworkInterface net) throws RemoteException {
        super();

        this.port = port;
        this.s = s;
        this.addr = addr;
        this.group = group;
        this.net = net;
        this.verbose = verbose;
        this.jedis = new Jedis("localhost", 6379);
        this.myServerId = ProcessHandle.current().pid();
        this.electionLock = new Object();
        this.replicateLock = new Object();
        this.coordinatorIP = "";

        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            System.err.println(e);
        }

        this.setShutdownHook(this);
        this.startAutoSaveTimer();
        this.startHeartbeatListener();
        // this.startHeartbeat();

        dao = new DataAccessObject(this.jedis);
    }

    private void bind() {
        try {
            System.setProperty("javax.net.ssl.keyStore", "../Server_Keystore");
            System.setProperty("javax.net.ssl.keyStorePassword", "qwerty");

            RMIClientSocketFactory rmiClientSocketFactory = new SslRMIClientSocketFactory();
            RMIServerSocketFactory rmiServerSocketFactory = new SslRMIServerSocketFactory();
            Server ccAuth = (Server) UnicastRemoteObject.exportObject(this, 0, rmiClientSocketFactory,
                    rmiServerSocketFactory);
            Registry registry = LocateRegistry.createRegistry(this.port, rmiClientSocketFactory,
                    rmiServerSocketFactory);
            registry.rebind("AsyncServer", ccAuth);
            System.out.println("AsyncServer" + " bound in registry");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception occurred: " + e);
        }
    }

    private void initialize() throws IOException {
        byte[] buf = new byte[4];
        DatagramPacket recv = new DatagramPacket(buf, buf.length, addr, discoveryPort);
        servers.add(InetAddress.getLocalHost().getHostAddress());
        System.out.println("Listening to Multicast Group");

        try {
            // Set timeout and discover group
            s.setSoTimeout(timeout);
            DatagramPacket hello = new DatagramPacket(Utility.getBytes(DISCOVER_GROUP), 4, addr, discoveryPort);
            s.send(hello);

            s.receive(recv);
            printDatagram(recv);

            if (Utility.getInt(buf) == LEAVE_GROUP) {
                servers.remove(recv.getAddress().getHostAddress());
                if (verbose) printGroup();
            } else if (!recv.getAddress().equals(InetAddress.getLocalHost())) {
                if (Utility.getInt(buf) == DISCOVER_GROUP) {
                    DatagramPacket ack = new DatagramPacket(Utility.getBytes(I_AM_HERE), 4, addr, discoveryPort);
                    s.send(ack);
                    if (!servers.contains(recv.getAddress().getHostAddress())) servers.add(recv.getAddress().getHostAddress());
                    if (verbose) printGroup();

                } else if (Utility.getInt(buf) == I_AM_HERE) {
                    if (!servers.contains(recv.getAddress().getHostAddress())) servers.add(recv.getAddress().getHostAddress());
                    if (verbose) {
                        System.err.println("Found a addr");
                        printGroup();
                    }
                }
            } else {
                // Only received This processes' multicast
                // Reset timeout and try again
                s.setSoTimeout(timeout); // try again
                s.receive(recv);
                printDatagram(recv);
                if (Utility.getInt(buf) == LEAVE_GROUP) {
                    servers.remove(recv.getAddress().getHostAddress());
                    if (verbose) printGroup();
                } else if (Utility.getInt(buf) == DISCOVER_GROUP) {
                    DatagramPacket ack = new DatagramPacket(Utility.getBytes(I_AM_HERE), 4, addr, discoveryPort);
                    s.send(ack);
                    if (!servers.contains(recv.getAddress().getHostAddress())) servers.add(recv.getAddress().getHostAddress());
                    if (verbose) printGroup();
                } else if (Utility.getInt(buf) == I_AM_HERE) {
                    if (!servers.contains(recv.getAddress().getHostAddress())) servers.add(recv.getAddress().getHostAddress());
                    if (verbose) {
                        System.err.println("Found a addr");
                        printGroup();
                    }
                }
            }
        } catch (SocketTimeoutException e) {
            // no one responded, so start a new addr
            s.setSoTimeout(default_timeout);
            if (verbose) {
                System.err.println("No response for discover addr. Starting a new addr");
                printGroup();
            }
        } finally {
            new Thread(() -> {
                startElection();
            }).start();
        }
        s.setSoTimeout(default_timeout);
    }

    // MAIN RMI ENTRYPOINT
    public synchronized void execute(Request work, Listener listener) throws java.rmi.RemoteException {
        // Keep execution in the remote call thread so RemoteServer.getClientHost() is
        // valid.
        new Thread(() -> {
            if (isCoordinator) {
                try {
                    lTime.getAndIncrement();
                    String result = (String) work.execute(this);
                    if (!replicateToBackups(work))
                        result = " - ERROR - Could not replicate request. Try again";
                    listener.workCompleted(result);
                } catch (RemoteException e) {
                    System.err.println(e);
                }
            } else {
                try {
                    listener.workCompleted("UH OH, LOOKS LIKE I'M NOT A COORDINATOR\nCoordinator IP - " + coordinatorIP);
                } catch (RemoteException e) {
                    System.err.println(e);
                }
            }
        }).start();
        return;
    }

    // private execute w/out listener for replication
    // NOT for coordinator use
    private synchronized void execute(Request work) {
        new Thread(() -> {
            try {
                work.execute(this);
            } catch (RemoteException e) {
                System.err.println(e);
            }
        }).start();
        return;
    }

    // RMI METHODS
    // CALLED IN THE REQUEST OBJECTS
    public synchronized String create(String login, String name, String password, String host)
            throws java.rmi.RemoteException {
        try {
            if (verbose) {
                System.out.println("Creating user: " + login);
            }
            if (password == null)
                password = defaultPassword;
            String hashedPassword = hashPassword(password);
            password = "";

            return dao.create(login, name, hashedPassword, host);
        } catch (Exception e) {
            System.err.println(e);
            return e.toString();
        }
    }

    public synchronized String lookup(String login) throws java.rmi.RemoteException {
        if (verbose) {
            System.out.println("Looking up user: " + login);
        }
        return dao.lookup(login);
    }

    public synchronized String reverseLookup(String uuid) throws java.rmi.RemoteException {
        if (verbose) {
            System.out.println("Reverse lookup for UUID: " + uuid);
        }
        return dao.reverseLookup(UUID.fromString(uuid));
    }

    public synchronized String modify(String oldLogin, String newLogin, String password)
            throws java.rmi.RemoteException {
        try {
            if (verbose) {
                System.out.println("Changing user: " + oldLogin + " to " + newLogin);
            }
            if (password == null)
                password = defaultPassword;
            String hashedPassword = hashPassword(password);
            password = "";

            return dao.modify(oldLogin, newLogin, hashedPassword);
        } catch (Exception e) {
            System.err.println(e);
            return e.toString();
        }
    }

    public synchronized String delete(String login, String password) throws java.rmi.RemoteException {
        try {
            if (verbose) {
                System.out.println("Deleting user: " + login);
            }
            if (password == null)
                password = defaultPassword;
            String hashedPassword = hashPassword(password);
            password = "";

            return dao.delete(login, hashedPassword);
        } catch (Exception e) {
            System.err.println(e);
            return e.toString();
        }
    }

    public synchronized String get(String option) throws java.rmi.RemoteException {
        if (verbose) {
            System.out.println("Fetching data: " + option);
        }
        return dao.get(option);
    }

    // MULTICAST METHODS
    private void listenForMulticast() throws IOException {
        // Expand buffer size significantly to accommodate serialized Java Objects
        byte[] buf = new byte[8192];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);
        System.out.println("In MultiCast Group (Listening for objects & ints)");

        while (true) {
            s.receive(recv);

            // Skip messages we sent ourselves
            if (recv.getAddress().equals(InetAddress.getLocalHost()))
                continue;

            // Check if the payload is exactly 4 bytes (Legacy integer commands)
            if (recv.getLength() == 4) {
                int cmd = Utility.getInt(recv.getData());
                if (cmd == DISCOVER_GROUP) {
                    DatagramPacket ack = new DatagramPacket(Utility.getBytes(I_AM_HERE), 4, addr, discoveryPort);
                    s.send(ack);
                    servers.add(recv.getAddress().getHostAddress());
                } else if (cmd == JOIN_GROUP || cmd == I_AM_HERE) {
                    servers.add(recv.getAddress().getHostAddress());
                } else if (cmd == LEAVE_GROUP) {
                    servers.remove(recv.getAddress().getHostAddress());
                    if (verbose) System.out.println(recv.getAddress().getHostAddress() + " has left the party");
                } else if (cmd == HEARTBEAT) {
                    lastHeartBeat = System.currentTimeMillis();
                    if (verbose) {System.out.println("Noticed heartbeat from " + recv.getAddress());}
                }
            } else {
                try {
                    // Ensure we only pass the exact bytes received, not the padded 8192 buffer
                    byte[] exactData = new byte[recv.getLength()];
                    System.arraycopy(recv.getData(), 0, exactData, 0, recv.getLength());

                    Object obj = Utility.getObject(exactData);
                    if (obj instanceof ServerMessage) {
                        ServerMessage msg = (ServerMessage) obj;
                        
                        if (msg.getType() == ServerMessage.Type.ELECTION || msg.getType() == ServerMessage.Type.COORDINATOR || msg.getType() == ServerMessage.Type.SUPPRESS) {
                            new Thread(() -> {handleElectionMessage(msg);}).start();
                        } else if (msg.getType() == ServerMessage.Type.REPLICATE) {
                            handleReplication(msg);
                        } else if (msg.getType() == ServerMessage.Type.ACK) {
                            // Only coordinators need to handle acks
                            if (isCoordinator) new Thread(() -> {handleAck(msg);}).start();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to deserialize incoming object: " + e);
                }
            }
        }
    }

    public void startElection() {
        if (verbose)
            System.out.println("Server " + myServerId + " starting election...");

        gotBullied = false;
        startedElection = true;
        ServerMessage electionMsg = new ServerMessage(ServerMessage.Type.ELECTION, myServerId);
        sendMulticastObject(electionMsg);

        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.err.println(e);
            }

            synchronized (electionLock) {
                electionLock.notify();
            }
        }).start();

        try {
            synchronized (electionLock) {
                electionLock.wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!gotBullied) {
            becomeCoordinator(true);
        }
    }

    private void becomeCoordinator(boolean newStartHeartbeat) {
        isCoordinator = true;
        startedElection = false;
        currentCoordinatorId = myServerId;
        if (newStartHeartbeat) {
            this.startHeartbeat();
        }
        if (verbose)
            System.out.println("Server " + myServerId + " is the new Coordinator!");
        sendMulticastObject(new ServerMessage(ServerMessage.Type.COORDINATOR, myServerId, ip));
    }

    public void handleElectionMessage(ServerMessage msg) {
        long senderId = msg.getSenderId();

        switch (msg.getType()) {
            case ELECTION:
                if (isCoordinator && senderId < myServerId) {
                    becomeCoordinator(false);
                    break;
                }
                if (senderId < myServerId) {
                    // Run this asynchronously so we don't block the incoming network message
                    // listener!
                    if (startedElection) sendMulticastObject(new ServerMessage(ServerMessage.Type.SUPPRESS, myServerId));
                    else new Thread(this::startElection).start();
                } else {
                    // Higher ID sent an election, we yield
                    isCoordinator = false;
                    stopHeartbeat();
                    
                    gotBullied = true;
                    startedElection = false;
                    synchronized (electionLock) {
                        electionLock.notify();
                    }
                }
                break;
            case SUPPRESS:
                if (startedElection && senderId > myServerId) {
                    isCoordinator = false;
                    stopHeartbeat();
                    
                    gotBullied = true;
                    startedElection = false;
                    synchronized (electionLock) {
                        electionLock.notify();
                    }
                }
                break;
            case COORDINATOR:
                if (senderId > myServerId) {
                    isCoordinator = false;
                    gotBullied = true;
                    startedElection = false;
                    currentCoordinatorId = senderId;
                    coordinatorIP = msg.getCoordinatorIP(); 
                    if (verbose)
                        System.out
                                .println("Server " + myServerId + " acknowledges Coordinator " + currentCoordinatorId);

                    synchronized (electionLock) {
                        electionLock.notify();
                    }
                }
                break;
        }
    }

    public void handleAck(ServerMessage msg) {
        if (servers.contains(msg.getSenderIP())) {
            acks.add(msg.getSenderIP());
            if (verbose) System.out.println("Ack Received from replica " + msg.getSenderIP());
        }

        if (acks.size() >= servers.size() - 1) synchronized (replicateLock) {replicateLock.notify();}
    }

    public boolean replicateToBackups(Request request) {
        if (!isCoordinator) {
            System.err.println("Only the coordinator can replicate writes.");
            return false;
        }

        long lTimeSnapshot = lTime.get();
        ServerMessage msg = new ServerMessage(request, lTimeSnapshot);

        // We expect ACKs from all other known servers
        acks.clear();

        // 1. Multicast PREPARE to Replicas
        // ONLY IF THERE ARE REPLICAS OTHER THAN COORDINATOR
        if (servers.size() > 1) {
            sendMulticastObject(msg);
            
            new Thread(() -> {
                try { Thread.sleep(3000); }
                catch (InterruptedException e) { System.err.println(e); }
                
                synchronized(replicateLock) { replicateLock.notify(); }
            }).start();
            
            try {
                synchronized (replicateLock) {
                    replicateLock.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (verbose) System.out.println("Acks Received: " + acks.size() + "/" + (servers.size() - 1));

        boolean retval = (acks.size() >= servers.size() - 1) ? true : false; 
        acks.clear();
        return retval;
    }

    public synchronized void handleReplication(ServerMessage msg) {
        // Coordinator issued COMMIT. Apply to local DataAccessObject/Jedis
        if (verbose)
            System.out.println("Committing locally on replica " + myServerId);
        try {
            lTime.getAndSet(Math.max(msg.getTimestamp(), lTime.get()));
            execute(msg.getRequest());
            sendMulticastObject(new ServerMessage(ServerMessage.Type.ACK, ip));
            if (verbose) System.out.println("Ack Sent to Coordinator - " + currentCoordinatorId);
        } catch (Exception e) {
            System.err.println("Replica failed to apply commit: " + e);
        }
    }

    // UTILITY METHODS
    private static String hashPassword(String input) throws NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));

        BigInteger number = new BigInteger(1, hash);

        // Convert message digest into hex value
        StringBuilder hexString = new StringBuilder(number.toString(16));

        // Pad with leading zeros
        while (hexString.length() < 64) {
            hexString.insert(0, '0');
        }

        return hexString.toString();
    }

    // Save Server State to Redis periodically
    private synchronized void saveState() {
        jedis.save();
    }

    private void startAutoSaveTimer() {
        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    saveState();
                }
                // TODO
                catch (Exception e) {
                    System.err.println(e);
                }
            }
        };

        new Thread(() -> {
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(task, 180000, 180000);
        }).start();
    }


    private synchronized void startHeartbeat() {

    if (heartbeatExecutor != null) {
        return;        
    }
    // 1. Safety check: close any existing executor to prevent thread leaks
    if (!isCoordinator) {
        stopHeartbeat();
    }
    // 2. Initialize with a custom ThreadFactory to ensure it's a Daemon thread
    heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread t = new Thread(runnable, "HeartbeatExecutor-Thread");
        t.setDaemon(true); 
        return t;
    });

    // 3. Schedule: Start immediately (0), repeat every 1000 milliseconds
    heartbeatExecutor.scheduleAtFixedRate(() -> {
        try {
            // Double-check state; self-cancel if we get demoted
            if (!isCoordinator) {
                stopHeartbeat();
                return;
            }
            
            DatagramPacket hello = new DatagramPacket(Utility.getBytes(HEARTBEAT), 4, addr, discoveryPort);
            s.send(hello);
            
            if (verbose) {
                System.out.println("Coordinator " + myServerId + ": Bump Bump...");
            }
        } catch (Exception e) {
            // Executors swallow exceptions by default; printing ensures you see it
            System.err.println("Heartbeat execution error: " + e.getMessage());
        }
    }, 0, 3000, TimeUnit.MILLISECONDS);
}

public synchronized void stopHeartbeat() {
    if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
        heartbeatExecutor.shutdownNow(); // Interrupts the sleeping thread instantly
        heartbeatExecutor = null;        // Clear reference for garbage collection
        if (verbose) {
            System.out.println("Heartbeat executor stopped.");
        }
    }
}

public synchronized void startHeartbeatListener() {
    if (heartbeatListenerExecutor == null || heartbeatListenerExecutor.isShutdown()) {
        
        heartbeatListenerExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "HeartbeatListener-Thread");
            t.setDaemon(true);
            return t;
        });

        // Start checking after 2 seconds, then check every 500 milliseconds
        heartbeatListenerExecutor.scheduleAtFixedRate(() -> {
            try {
                // Only care about missing heartbeats if we are NOT the coordinator
                if (!isCoordinator) {
                    long timeSinceLast = System.currentTimeMillis() - lastHeartBeat;
                    
                    if (timeSinceLast > 4000) {
                        System.out.println("Timeout: No heartbeat for " + timeSinceLast + "ms. Initiating election!");
                        
                        // Reset the clock locally so we don't spam multiple elections
                        // while the network is busy electing the new coordinator
                        lastHeartBeat = System.currentTimeMillis(); 
                        
                        startElection();
                    }
                }
            } catch (Exception e) {
                System.err.println("Heartbeat listener error: " + e.getMessage());
            }
        }, 2000, 5000, TimeUnit.MILLISECONDS);
    }
}


    /**
     * Sets a shutdown hook that saves state before shutting down
     */
    private void setShutdownHook(IdServer server) {
        Thread shutdownHook = new Thread(() -> {
            saveState();
            System.out.println("Server shutting down...");
            server.leave();
        });

        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void leave() {
        try {
            DatagramPacket bye = new DatagramPacket(Utility.getBytes(LEAVE_GROUP), 4, addr, discoveryPort);
            s.send(bye);
            servers.clear();
            s.leaveGroup(group, net);
            s.close();
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    private void printGroup() {
        Iterator<String> itr = servers.iterator();
        while (itr.hasNext()) {
            System.err.println(itr.next());
        }
    }

    private void printDatagram(DatagramPacket pkt) throws UnknownHostException, IOException {
        String packetType = "";
        switch (Utility.getInt(pkt.getData())) {
            case 1:
                packetType = "DISCOVER_GROUP";
                break;
            case 2:
                packetType = "LEAVE_GROUP";
                break;
            case 3:
                packetType = "JOIN_GROUP";
                break;
            case 4:
                packetType = "I_AM_HERE";
                break;
            default:
                break;
        }
        System.out.println("Server " + InetAddress.getLocalHost() + ": Received packet type " + packetType + " from "
                + pkt.getAddress());
    }

    private void sendMulticastObject(Object obj) {
        try {
            byte[] data = Utility.getBytes(obj);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, discoveryPort);
            s.send(packet);
        } catch (IOException e) {
            System.err.println("Failed to multicast object: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int registryPort = 1099;
        boolean verbose = false;

        if (args.length > 3) {
            System.err.println("Usage: java IdServer [--numport <registry port>] [--verbose]");
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--numport" -> {
                    if (i + 1 < args.length) {
                        registryPort = Integer.parseInt(args[++i]);
                    }
                }
                case "--verbose" -> verbose = true;
                default -> {
                    System.err.println("Usage: java IdServer [--numport <registry port>] [--verbose]");
                    System.exit(1);
                }
            }
        }

        try {

            System.setProperty("javax.net.ssl.keyStore", "../Server_Keystore");
            System.setProperty("javax.net.ssl.keyStorePassword", "qwerty");
            System.setProperty("java.security.policy", "../mysecurity.policy");

            System.setProperty("javax.net.ssl.trustStore", "../Client_Truststore");
            System.setProperty("javax.net.ssl.trustStorePassword", "qwerty");

            InetAddress addr = InetAddress.getByName(mcastAddress);
            SocketAddress group = new InetSocketAddress(addr, discoveryPort);
            MulticastSocket s = new MulticastSocket(discoveryPort);
            NetworkInterface net = NetworkInterface.getByName(networkInterface);

            s.setNetworkInterface(net);
            s.joinGroup(group, net);

            IdServer server = new IdServer(registryPort, verbose, addr, group, s, net);

            // Setup server/client rmi
            server.bind();
            server.initialize();
            
            // Discover multicast group and get first response
            // Spawn a constant listener thread
            new Thread(() -> {
                try {
                    server.listenForMulticast();
                } catch (IOException e) {
                    System.err.println(e);
                }
            }).start();

        } catch (java.io.IOException e) {
            System.err.println("MyServer: problem registering server");
            System.err.println(e);
        }
    }
}