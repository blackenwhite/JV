#include "httplib.h"
#include <iostream>
#include <string>
#include <thread>
#include <chrono>
using namespace std;
int main(int argc, char** argv) {
    string url = argc > 1 ? argv[1] : "ws://localhost:8080/deploys/1/logs/ws";
    httplib::ws::WebSocketClient cli(url);
    if (!cli.connect()) {
        cerr << "connect failed\n";
        return 1;
    }
    cli.set_read_timeout(chrono::seconds(1));
    thread([&] {
        this_thread::sleep_for(chrono::milliseconds(500));
        cli.send("ping");
    }).detach();
    string msg;
    while (cli.is_open()) {
        auto result = cli.read(msg);
        if (result == httplib::ws::ReadResult::Fail) break;
        if (result == httplib::ws::ReadResult::Text) cout << msg << "\n";
    }
}