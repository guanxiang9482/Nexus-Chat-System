import socket, struct

HOST, PORT = '127.0.0.1', 8080

with socket.create_connection((HOST, PORT)) as s:
    # Read the server's welcome message (length-prefixed)
    raw_len = s.recv(4)
    msg_len = struct.unpack('>I', raw_len)[0]
    welcome = s.recv(msg_len).decode('utf-8')
    print(f"Server says: {welcome}")

    # Send a length-prefixed message
    payload = b"Hello Nexus!"
    frame = struct.pack('>I', len(payload)) + payload
    s.sendall(frame)

    # Read the echo back
    raw_len = s.recv(4)
    msg_len = struct.unpack('>I', raw_len)[0]
    echo = s.recv(msg_len).decode('utf-8')
    print(f"Echo: {echo}")