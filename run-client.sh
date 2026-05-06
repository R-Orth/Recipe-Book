#!/bin/bash

# ==========================================
# 1. Initialize variables & defaults
# ==========================================
# Global variables
SERVER=""
PORT=""
PASSWORD=""
COMMAND=""

# Command-specific variables
LOGIN=""
REALNAME=""
UUID_VAL=""
OLD_LOGIN=""
NEW_LOGIN=""
GET_TYPE=""

# ==========================================
# 2. Define the Usage / Help function
# ==========================================
usage() {
    echo "Usage: $0 --server <host> [--numport <port>] <query>"
    echo "Queries:"
    echo "  -c, --create <login> [<realname>] [-p <pass>]"
    echo "  -l, --lookup <login>"
    echo "  -r, --reverse-lookup <UUID>"
    echo "  -m, --modify <old> <new> [-p <pass>]"
    echo "  -d, --delete <login> [-p <pass>]"
    echo "  -g, --get users|uuids|all"
    exit 1
}

# ==========================================
# 3. Parse command-line arguments
# ==========================================
# Display usage if no arguments are provided
if [[ "$#" -eq 0 ]]; then
    usage
fi

while [[ "$#" -gt 0 ]]; do
    case $1 in
        # Global Flags
        --server)
            SERVER="$2"
            shift 2
            ;;
        --numport)
            PORT="$2"
            shift 2
            ;;
        -p|--pass)
            PASSWORD="$2"
            shift 2
            ;;
            
        # Queries / Commands
        -c|--create)
            COMMAND="create"
            LOGIN="$2"
            # Check if the next argument exists and is NOT another flag
            if [[ "$#" -ge 3 && "$3" != -* ]]; then
                REALNAME="$3"
                shift 3
            else
                shift 2
            fi
            ;;
        -l|--lookup)
            COMMAND="lookup"
            LOGIN="$2"
            shift 2
            ;;
        -r|--reverse-lookup)
            COMMAND="reverse-lookup"
            UUID_VAL="$2"
            shift 2
            ;;
        -m|--modify)
            COMMAND="modify"
            OLD_LOGIN="$2"
            NEW_LOGIN="$3"
            shift 3
            ;;
        -d|--delete)
            COMMAND="delete"
            LOGIN="$2"
            shift 2
            ;;
        -g|--get)
            COMMAND="get"
            GET_TYPE="$2"
            shift 2
            ;;
            
        # Help & Unknown
        -h|--help)
            usage
            ;;
        *)
            echo "Error: Unknown parameter passed: $1"
            usage
            ;;
    esac
done

# ==========================================
# 4. Validate required parameters
# ==========================================

# 4a. Validate Global Requirements
if [[ -z "$SERVER" ]]; then
    echo "Error: Missing required global parameter --server <host>"
    usage
fi

if [[ -z "$COMMAND" ]]; then
    echo "Error: You must specify a query (e.g., -c, -l, -r, -m, -d, -g)"
    usage
fi

# 4b. Validate Command-Specific Requirements
case "$COMMAND" in
    create)
        if [[ -z "$LOGIN" ]]; then
            echo "Error: --create requires a <login>"
            usage
        fi
        ;;
    lookup)
        if [[ -z "$LOGIN" ]]; then
            echo "Error: --lookup requires a <login>"
            usage
        fi
        ;;
    reverse-lookup)
        if [[ -z "$UUID_VAL" ]]; then
            echo "Error: --reverse-lookup requires a <UUID>"
            usage
        fi
        ;;
    modify)
        if [[ -z "$OLD_LOGIN" || -z "$NEW_LOGIN" ]]; then
            echo "Error: --modify requires <old> and <new> logins"
            usage
        fi
        ;;
    delete)
        if [[ -z "$LOGIN" ]]; then
            echo "Error: --delete requires a <login>"
            usage
        fi
        ;;
    get)
        if [[ "$GET_TYPE" != "users" && "$GET_TYPE" != "uuids" && "$GET_TYPE" != "all" ]]; then
            echo "Error: --get requires 'users', 'uuids', or 'all'"
            usage
        fi
        ;;
esac

# ==========================================
# 5. Main Script Logic (Execution)
# ==========================================
echo "--- Parsed Configuration ---"
echo "Server:   $SERVER"
echo "Port:     ${PORT:-[Not Set]}"
echo "Command:  $COMMAND"
QUERY="java -Djdk.rmi.ssl.client.enableEndpointIdentification=false -cp ".:./lib/*" client/IdClient --server $SERVER"
[[ -n "$PORT" ]] && QUERY+=" --numport $PORT"

# Execute logic based on the chosen command
case "$COMMAND" in
    create)
        echo "Action:   Creating user '$LOGIN'"
        QUERY+=" -c $LOGIN"
        [[ -n "$REALNAME" ]] && { 
            echo "Realname: $REALNAME"
            QUERY+=" $REALNAME"
        }
        [[ -n "$PASSWORD" ]] && {
            echo "Password: $PASSWORD"
            QUERY+=" -p $PASSWORD"
        }
        ;;
    lookup)
        echo "Action:   Looking up '$LOGIN'"
        QUERY+=" -l $LOGIN"
        ;;
    reverse-lookup)
        echo "Action:   Reverse looking up UUID '$UUID_VAL'"
        QUERY+=" -r $UUID_VAL"
        ;;
    modify)
        echo "Action:   Modifying '$OLD_LOGIN' to '$NEW_LOGIN'"
        QUERY+=" -m $OLD_LOGIN $NEW_LOGIN"
        [[ -n "$PASSWORD" ]] && {
            echo "Password: [Provided]"
            QUERY+=" -p $PASSWORD"
        }
        ;;
    delete)
        echo "Action:   Deleting user '$LOGIN'"
        QUERY+=" -d $LOGIN"
        [[ -n "$PASSWORD" ]] && {
            echo "Password: [Provided]"
            QUERY+=" -p $PASSWORD"
        }
        ;;
    get)
        echo "Action:   Getting $GET_TYPE"
        QUERY+=" -g $GET_TYPE"
        ;;
esac
echo
echo $QUERY
$QUERY
echo "----------------------------"