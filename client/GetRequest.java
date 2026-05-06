package client;

import server.Server;
import server.Request;

public class GetRequest extends Request {
    
    private String type;

    public GetRequest(String type) {
        this.type = type;
    }

    @Override
    public Object execute(Server context) throws java.rmi.RemoteException {
        return context.get(this.type);
    }
}
