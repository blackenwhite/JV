import socketio

sio = socketio.Client()

@sio.event
def connect():
    print("Connected to the server")

@sio.event
def disconnect():
    print("Disconnected from the server")


@sio.on("chat_message")
def receive_message(message):
    print(f"\nNew message: {message}")

sio.connect('http://localhost:8000')

print("Connected to the server. Type your message and press Enter.")
print("Type 'quit' to stop.")

while True:
    message = input("> ")

    if message.lower() == 'quit':
        break

    sio.emit("chat_message", message)

sio.disconnect()
print("Disconnected from the server. Goodbye!")