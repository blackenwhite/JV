# Real-Time Chat Prototype

## What we built

A basic real-time chat application using Python and Socket.IO.

## Technologies

- Python
- `python-socketio`
- Uvicorn

## Files

- `server.py` — Runs the Socket.IO server.
- `client.py` — Connects to the server and provides a command-line chat client.
- `requirements.txt` — Lists the required Python packages.

## Features implemented

- Persistent Socket.IO client-server connection
- Connection and disconnection handling
- Sending chat messages through named Socket.IO events
- Broadcasting messages to all connected clients
- Usernames sent during connection authentication
- Server-side tracking of connected users
- Displaying the sender's username with each message
- Testing with multiple simultaneous clients

## Message flow

```text
Client connects with a username
        ↓
Server stores the username
        ↓
Client emits a chat_message event
        ↓
Server adds the username
        ↓
Server broadcasts the message to all clients
```

## Running the application

Start the server:

```bash
python server.py
```

Start one or more clients in separate terminals:

```bash
python client.py
```

This prototype does not include chat rooms, private messaging, message persistence, authentication, or a browser interface.
