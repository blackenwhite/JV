import socketio

sio = socketio.Client()

@sio.event
def connect():
    print("Connected to the server")

@sio.event
def disconnect():
    print("Disconnected from the server")


sio.connect('http://localhost:8000')
print("Client is running. Press Ctrl+C to stop.")

sio.wait()