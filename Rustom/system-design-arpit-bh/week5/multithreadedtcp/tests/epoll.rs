#![cfg(target_os = "linux")]

use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::thread;
use std::time::{Duration, Instant};

use multithreadedtcp::{EpollServer, PING, PONG};

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
fn a_silent_client_does_not_block_a_second_client() {
    let addr: SocketAddr = "127.0.0.1:0".parse().unwrap();
    let server = EpollServer::bind(addr).unwrap();
    let bound = server.local_addr().unwrap();

    let server_thread = thread::spawn(move || server.serve(2));

    let mut client_a = connect_retry(bound);
    thread::sleep(Duration::from_millis(50));

    let mut client_b = connect_retry(bound);
    client_b.write_all(PING.as_bytes()).unwrap();
    client_b
        .set_read_timeout(Some(Duration::from_secs(2)))
        .unwrap();

    let mut reply_b = String::new();
    client_b.read_to_string(&mut reply_b).unwrap();
    assert_eq!(reply_b, PONG);

    client_a.write_all(PING.as_bytes()).unwrap();
    let mut reply_a = String::new();
    client_a.read_to_string(&mut reply_a).unwrap();
    assert_eq!(reply_a, PONG);

    let thread_ids = server_thread
        .join()
        .expect("server thread panicked")
        .unwrap();
    assert!(!thread_ids.is_empty());
    let first = thread_ids[0];
    assert!(
        thread_ids.iter().all(|id| *id == first),
        "more than one thread handled sockets: {thread_ids:?}"
    );
}
