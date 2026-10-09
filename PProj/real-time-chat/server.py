import socketio
import uvicorn

sio = socketio.AsyncServer(async_mode='asgi', 
            cors_allowed_origins='*',
            )

app = socketio.ASGIApp(sio)

@sio.event
async def connect(sid, environ, auth):
    print(f"Client connected: {sid}")


@sio.on("chat_message")
async def handle_chat_message(sid, message):
    print(f"Message from {sid}: {message}")

    await sio.emit("chat_message", message, room=sid)


@sio.event
async def disconnect(sid):
    print(f"Client disconnected: {sid}")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
