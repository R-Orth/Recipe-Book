package server;
import redis.clients.jedis.Jedis;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;

public class DataAccessObject implements DataAccessObjectInterface {

    private Jedis jedis;
    

    public DataAccessObject(Jedis jedis) {
        this.jedis = jedis;
    }

    @Override
    public String create( String loginname, String realname, String password, String ip) {
        
        if (jedis.exists(loginname)) return "User with login \"" + loginname + "\" already exists";

        Map<String, String> userData = new HashMap<>();

        String retString = UUID.randomUUID().toString();

        Date now = new Date();

        userData.put("login", loginname);
        userData.put("uuid", retString);
        userData.put("password", password);
        userData.put("realname", realname);
        userData.put("ip", ip);
        userData.put("createDate", "" + now.getTime());
        userData.put("modifyDate", "" + now.getTime());

        jedis.hset(loginname, userData);
        
        return retString;
    }

    @Override
    public String lookup(String loginname) {
        String retString = "";
        if (jedis.exists(loginname)) {
            for (Map.Entry<String, String> entry : jedis.hgetAll(loginname).entrySet()) {
                retString += entry.getKey() + ": " + entry.getValue() + "\n";
            }
        } else {
            retString = "No user with login \"" + loginname + "\" exists";
        }
        
        return retString;
    }

    @Override
    public String reverseLookup(UUID uuid) {
        String retString = "";
        for (String key : jedis.keys("*")) {
            if (jedis.hget(key, "uuid").equals(uuid.toString())) {
                for (Map.Entry<String, String> entry : jedis.hgetAll(key).entrySet()) {
                    retString += entry.getKey() + ": " + entry.getValue() + "\n";
                }
                break;
            }
        }
        return retString;
    }

    @Override
    public String modify(String oldLoginname, String newLoginname, String password) {
        String retString = "";
        Date now = new Date();

        if (jedis.exists(oldLoginname) && !jedis.exists(newLoginname) && jedis.hget(oldLoginname, "password").equals(password)) {
            jedis.hset(newLoginname, "login", newLoginname);
            jedis.rename(oldLoginname, newLoginname);
            jedis.hset(newLoginname, "modifyDate", "" + now.getTime());
            retString = "Successfully modified user " + oldLoginname + " to " + newLoginname;
        } else {
            retString = "Failed to change user " + oldLoginname + " to " + newLoginname;
        }

        return retString;
    }

    @Override
    public String delete(String loginname, String password) {
        String retString = "";
        if (jedis.exists(loginname) && jedis.hget(loginname, "password").equals(password)) {
            jedis.del(loginname);
            retString = "Successfully deleted user " + loginname;
        } else {
            retString = "Failed to delete user " + loginname;
        }
        return retString;
    }

    @Override
    public String get(String getType) {
        String retString = "";
        if (getType.equals("users")) {
            for (String key : jedis.keys("*")) {
                retString += jedis.hget(key, "login") + "\n";
            }
            
        } else if (getType.equals("uuids")) {
            for (String key : jedis.keys("*")) {
                retString += jedis.hget(key, "uuid") + "\n";
            }
        } else if (getType.equals("all")) {
            for (String key : jedis.keys("*")) {
                for (String field : jedis.hkeys(key)) {
                    retString += jedis.hget(key, field) + "\n";
                }
            }
        } 

        return retString;
    }

    
}
