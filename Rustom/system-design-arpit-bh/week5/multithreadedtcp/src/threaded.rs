use std::io::{self, BufRead, BufReader, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::thread;
use std::time::Duration;

use crate::PONG;

pub struct ThreadedServer {
    listener: TcpListener,
    work_delay: Duration,
}

impl ThreadedServer {
    pub fn bind(addr: SocketAddr) -> io::Result<Self> {
        let listener = TcpListener::bind(addr)?;
        Ok(Self {
            listener,
            work_delay: Duration::ZERO,
        })
    }

    pub fn local_addr(&self) -> io::Result<SocketAddr> {
        self.listener.local_addr()
    }

    pub fn with_work_delay(self, delay: Duration) -> Self {
        Self {
            listener: self.listener,
            work_delay: delay,
        }
    }

    pub fn serve(self, connection_limit: usize) -> io::Result<()> {
        let mut workers = Vec::with_capacity(connection_limit);
        for _ in 0..connection_limit {
            let (stream, _peer) = self.listener.accept()?;
            let delay = self.work_delay;
            workers.push(thread::spawn(move || handle(stream, delay)));
        }
        for worker in workers {
            worker.join().expect("worker panicked")?;
        }
        Ok(())
    }
}

fn handle(mut stream: TcpStream, delay: Duration) -> io::Result<()> {
    {
        let mut reader = BufReader::new(&mut stream);
        let mut line = String::new();
        reader.read_line(&mut line)?;
    }
    thread::sleep(delay);
    stream.write_all(PONG.as_bytes())?;
    Ok(())
}
