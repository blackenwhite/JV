use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::thread;
use std::time::{Duration, Instant};

use multithreadedtcp::{PING, PONG, ThreadedServer};

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

fn ping(addr: SocketAddr) -> String {
    let mut client = connect_retry(addr);
    client.write_all(PING.as_bytes()).unwrap();
    let mut reply = String::new();
    client.read_to_string(&mut reply).unwrap();
    reply
}

#[test]
fn two_clients_get_pong_without_waiting_for_each_other() {
    let delay = Duration::from_millis(400);
    let addr: SocketAddr = "127.0.0.1:0".parse().unwrap();
    let server = ThreadedServer::bind(addr).unwrap().with_work_delay(delay);
    let bound = server.local_addr().unwrap();

    let server_thread = thread::spawn(move || server.serve(2));

    let started = Instant::now();
    let client_a = thread::spawn(move || ping(bound));
    let client_b = thread::spawn(move || ping(bound));

    assert_eq!(client_a.join().unwrap(), PONG);
    assert_eq!(client_b.join().unwrap(), PONG);

    let elapsed = started.elapsed();
    assert!(
        elapsed < delay + Duration::from_millis(250),
        "replies were sequential: {elapsed:?}"
    );

    server_thread
        .join()
        .expect("server thread panicked")
        .unwrap();
}
