use std::io::{self, BufRead, BufReader, Write};
use std::net::{SocketAddr, TcpListener};

use crate::PONG;

pub struct SingleConnectionServer {
    listener: TcpListener,
}

impl SingleConnectionServer {
    pub fn bind(addr: SocketAddr) -> io::Result<Self> {
        let listener = TcpListener::bind(addr)?;
        Ok(Self { listener })
    }

    pub fn local_addr(&self) -> io::Result<SocketAddr> {
        self.listener.local_addr()
    }
    pub fn serve(self) -> io::Result<()> {
        let (mut stream, _peer) = self.listener.accept()?;
        {
            let mut reader = BufReader::new(&mut stream);
            let mut line = String::new();
            reader.read_line(&mut line)?;
        }
        stream.write_all(PONG.as_bytes())?;
        Ok(())
    }
}
