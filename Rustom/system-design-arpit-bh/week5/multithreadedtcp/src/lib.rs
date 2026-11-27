pub mod single;
pub mod threaded;

#[cfg(target_os = "linux")]
pub mod epoll;

pub use single::SingleConnectionServer;
pub use threaded::ThreadedServer;

#[cfg(target_os = "linux")]
pub use epoll::EpollServer;
pub const PING: &str = "ping\n";
pub const PONG: &str = "pong\n";
