use std::collections::HashMap;
use std::io::{self, Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::os::fd::{AsRawFd, RawFd};
use std::thread::{self, ThreadId};

use nix::sys::epoll::{Epoll, EpollCreateFlags, EpollEvent, EpollFlags};

use crate::PONG;

struct Connection {
    stream: TcpStream,
    buf: Vec<u8>,
}

pub struct EpollServer {
    listener: TcpListener,
}

impl EpollServer {
    pub fn bind(addr: SocketAddr) -> io::Result<Self> {
        let listener = TcpListener::bind(addr)?;
        Ok(Self { listener })
    }

    pub fn local_addr(&self) -> io::Result<SocketAddr> {
        self.listener.local_addr()
    }

    pub fn serve(self, connection_limit: usize) -> io::Result<Vec<ThreadId>> {
        self.listener.set_nonblocking(true)?;
        let listener_fd = self.listener.as_raw_fd();
        // epoll_create1
        let epoll = Epoll::new(EpollCreateFlags::empty()).map_err(io::Error::from)?;
        // epoll_ctl(ADD) on the listening socket
        epoll
            .add(
                &self.listener,
                EpollEvent::new(EpollFlags::EPOLLIN, listener_fd as u64),
            )
            .map_err(io::Error::from)?;
        let mut connections: HashMap<RawFd, Connection> = HashMap::new();
        let mut events = [EpollEvent::empty(); 16];
        let mut completed = 0;
        let mut thread_ids = Vec::new();
        while completed < connection_limit {
            thread_ids.push(thread::current().id());
            // epoll_wait. 1000 ms, then loop. None means "no events this time".
            let ready = epoll.wait(&mut events, 1000u16).map_err(io::Error::from)?;
            for event in events.iter().take(ready) {
                thread_ids.push(thread::current().id());
                let fd = event.data() as RawFd;
                if fd == listener_fd {
                    accept_ready(&self.listener, &epoll, &mut connections)?;
                    continue;
                }
                let finished = match connections.get_mut(&fd) {
                    Some(conn) => read_ready(conn)?,
                    None => false,
                };
                if finished {
                    if let Some(conn) = connections.remove(&fd) {
                        let _ = epoll.delete(&conn.stream);
                        completed += 1;
                    }
                }
            }
        }
        Ok(thread_ids)
    }
}

fn accept_ready(
    listener: &TcpListener,
    epoll: &Epoll,
    connections: &mut HashMap<RawFd, Connection>,
) -> io::Result<()> {
    loop {
        match listener.accept() {
            Ok((stream, _peer)) => {
                stream.set_nonblocking(true)?;
                let fd = stream.as_raw_fd();
                epoll
                    .add(&stream, EpollEvent::new(EpollFlags::EPOLLIN, fd as u64))
                    .map_err(io::Error::from)?;
                connections.insert(
                    fd,
                    Connection {
                        stream,
                        buf: Vec::new(),
                    },
                );
            }
            Err(err) if err.kind() == io::ErrorKind::WouldBlock => return Ok(()),
            Err(err) if err.kind() == io::ErrorKind::Interrupted => continue,
            Err(err) => return Err(err),
        }
    }
}
fn read_ready(conn: &mut Connection) -> io::Result<bool> {
    let mut tmp = [0u8; 512];
    loop {
        match conn.stream.read(&mut tmp) {
            Ok(0) => return Ok(false),
            Ok(n) => {
                conn.buf.extend_from_slice(&tmp[..n]);
                if conn.buf.contains(&b'\n') {
                    conn.stream.write_all(PONG.as_bytes())?;
                    return Ok(true);
                }
            }
            Err(err) if err.kind() == io::ErrorKind::WouldBlock => return Ok(false),
            Err(err) if err.kind() == io::ErrorKind::Interrupted => continue,
            Err(err) => return Err(err),
        }
    }
}
