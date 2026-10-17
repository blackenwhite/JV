#include "httplib.h"
#include <iostream>
using namespace std;

int main() {
    httplib::Server svr;

    svr.Get("/hello", [](const httplib::Request&, httplib::Response& res){
        res.set_content("Hello from C++", "text/plain");
    });

    cout<< "listening on http://localhost:8080\n";
    svr.listen("0.0.0.0", 8080);
}