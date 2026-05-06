package client;

import java.util.UUID;

import server.Server;
import server.Request;

public class ReverseLookupRequest extends Request {
    
    private String uuid;

    public ReverseLookupRequest(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public Object execute(Server context) throws java.rmi.RemoteException {
        return context.reverseLookup(this.uuid);
    }
}
