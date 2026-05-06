package client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import javax.rmi.ssl.SslRMIClientSocketFactory;

import server.Server;

import server.Listener;
import server.Request;

public class  IdClient extends java.rmi.server.UnicastRemoteObject implements Listener {


    private Server server;
    private Object allDone;
    private int reqCount;
    private boolean reqSent;

    private String[] serverIPs = 
    {"10.29.3.50",  //00
                    //01
    "10.29.3.42",   //02
    "10.29.2.131",  //03
    "10.29.3.130",  //04
    "10.29.3.135",  //05
    "10.29.3.138",  //06
    "10.29.3.32",   //07
    "10.29.3.141",  //08    
    };

    

    public IdClient(String host, int port) throws RemoteException {

        System.setProperty("javax.net.ssl.trustStore", "../Client_Truststore");
        System.setProperty("java.security.policy", "../mysecurity.policy");
        System.setProperty("javax.net.ssl.trustStorePassword", "qwerty");
        allDone = new Object();
        reqCount = 0;
        reqSent = false;


            try {
                // Use SSL socket factory for the registry lookup
                SslRMIClientSocketFactory sslSocketFactory = new SslRMIClientSocketFactory();
                Registry registry = LocateRegistry.getRegistry(host, port, sslSocketFactory);
                server = (Server) registry.lookup("AsyncServer");
                
            } catch (NotBoundException e) {
                System.err.println(e);
            }
    }

    private void executeRequest(int type, String login, String real, String password, String uuid, String newLogin, String getOpt) {
        try {
            Request r = null;
            switch (type) {
                case 0 :

                    // password may be null at this point, null check handled in server
                    String host = "";
                    try {
                        host = InetAddress.getLocalHost().getHostAddress();
                    } catch (UnknownHostException e) { 
                        System.err.println(e);
                    }
                    r = new CreateRequest(login, real, password, host);

                    break;
            
                case 1 :

                    r = new LookupRequest(login);

                    break;
                
                case 2 :
                    
                    r = new ReverseLookupRequest(uuid);
                    
                    break;
                    
                case 3 :
                    
                    r = new ModifyRequest(login, newLogin, password);
                    
                    break;
                    
                case 4 :
                    
                    r = new DeleteRequest(login, password);
                    
                    break;
                        
                case 5 :
                    
                    r = new GetRequest(getOpt);
                    
                    break;    
            } 

            this.server.execute(r, this);
            this.incCount();
            reqSent = true;

            try {
				synchronized (allDone) {
					allDone.wait();
				}
			} catch (InterruptedException e) {
				System.err.println(e);
			}

            System.exit(0);
        } catch (RemoteException e) {
            System.err.println();
        }
                            
    }

    private static void printUsage() {
        System.out.println("Usage: java IdClient --server <host> [--numport <port>] <query>");
        System.out.println("Queries:");
        System.out.println("  -c, --create <login> [<realname>] [-p <pass>]");
        System.out.println("  -l, --lookup <login>");
        System.out.println("  -r, --reverse-lookup <UUID>");
        System.out.println("  -m, --modify <old> <new> [-p <pass>]");
        System.out.println("  -d, --delete <login> [-p <pass>]");
        System.out.println("  -g, --get users|uuids|all");
    }

    @Override
    public void workCompleted(Object result) throws RemoteException {
        System.out.println("IdClient: recvd notification from server");
        System.out.flush();
        System.out.println(result);
        this.decCount();
        synchronized (allDone) {
            if (reqCount == 0 && reqSent) allDone.notify();
        }
        return;
    }
    
    
    
    
    private synchronized void incCount() {
        reqCount++;
    }


    private synchronized void decCount() {
        reqCount--;
    }


    
    
    public static void main(String[] args) {

        // Default values
        String serverHost = null;
        int port = 1099; // Default port example
        
        // Query variables
        int queryType = -1;
        String loginName = null;
        String realName = System.getProperty("user.name");
        String password = null;
        String uuid = null;
        String newLoginName = null;
        String getOption = null;
    
        try {
            int i = 0;
            while (i < args.length) {
                String arg = args[i];
    
                switch (arg) {
                    case "--server":
                    case "-s":
                        serverHost = args[++i];
                        break;
                    case "--numport":
                    case "-n":
                        port = Integer.parseInt(args[++i]);
                        break;
                    
                    // --- Query Types ---
                    case "--create":
                    case "-c":
                        queryType = 0;
                        loginName = args[++i];
                        // Check if next arg is a real name (not a flag starting with -)
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            realName = args[++i];
                        }
                        break;
                    case "--lookup":
                    case "-l":
                        queryType = 1;
                        loginName = args[++i];
                        break;
                    case "--reverse-lookup":
                    case "-r":
                        queryType = 2;
                        uuid = args[++i];
                        break;
                    case "--modify":
                    case "-m":
                        queryType = 3;
                        loginName = args[++i];
                        newLoginName = args[++i];
                        break;
                    case "--delete":
                    case "-d":
                        queryType = 4;
                        loginName = args[++i];
                        break;
                    case "--get":
                    case "-g":
                        queryType = 5;
                        getOption = args[++i];
                        if (!getOption.equals("users") && !getOption.equals("uuids") && 
                                    !getOption.equals("all")) {
                            System.err.println("Unknown argument: " + getOption);
                            return;
                        };
                        break;
                    
                    // --- Modifiers ---
                    case "--password":
                    case "-p":
                        password = args[++i];
                        break;
                    
                    default:
                        System.err.println("Unknown argument: " + arg);
                        return;
                }
                i++;
            }
    
            if (serverHost == null || queryType < 0) {
                printUsage();
                return;
            }
    
            // --- At this point, you would trigger your Server Logic ---
            IdClient client = new IdClient(serverHost, port);
            client.executeRequest(queryType, loginName, realName, password, uuid, newLoginName, getOption);
    
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Error: Missing arguments for option.");
            printUsage();
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a number.");
        } catch (RemoteException e) {
            System.err.println(e);
        }
    }
}
