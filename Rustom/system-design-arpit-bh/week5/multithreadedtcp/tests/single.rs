use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::thread;
use std::time::{Duration, Instant};

use multithreadedtcp::{PING, PONG, SingleConnectionServer};

fn connect_retry(addr: SocketAddr) -> TcpStream {
    let deadline = Instant::now() + Duration::from_secs(1);
    loop {
        match TcpStream::connect_timeout(&addr, Duration::from_millis(50)) {
            Ok(stream) => return stream,
            Err(_) if Instant::now() < deadline => continue,
            Err(err) => panic!("could not connect to {addr}: {err}"),
        }
    }
}

#[test]
fn one_client_gets_pong_then_the_server_returns() {
    let addr: SocketAddr = "127.0.0.1:0".parse().unwrap();
    let server = SingleConnectionServer::bind(addr).unwrap();
    let bound = server.local_addr().unwrap();

    let handle = thread::spawn(move || server.serve());

    let mut client = connect_retry(bound);
    client.write_all(PING.as_bytes()).unwrap();

    let mut reply = String::new();
    client.read_to_string(&mut reply).unwrap();
    assert_eq!(reply, PONG);

    let serve_result = handle.join().expect("server thread panicked");
    assert!(serve_result.is_ok());
}
