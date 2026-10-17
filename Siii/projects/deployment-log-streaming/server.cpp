#include "httplib.h"
#include <iostream>
using namespace std;

struct LogLine
{
    long id;
    string text;
};

struct Deploy
{
    long id;
    string status = "running";
    vector<LogLine> lines;
    mutex mu;
};

mutex deploys_mu;
map<long, shared_ptr<Deploy>> deploys;
atomic<long> next_deploy_id{1};

const vector<string> script = {
    "Cloning repo...",
    "Installing dependencies...",
    "Buidling image...",
    "Running tests...",
    "Pushing image...",
    "Deploy complete"};

void run_deploy(shared_ptr<Deploy> deploy)
{
    long line_id = 1;
    for (const string &text : script)
    {
        this_thread::sleep_for(chrono::milliseconds(700));
        lock_guard<mutex> lock(deploy->mu);
        deploy->lines.push_back({line_id++, text});
        if (text == "Deploy complete")
        {
            deploy->status = "done";
        }
    }
}

string snapshot(shared_ptr<Deploy> deploy)
{
    lock_guard<mutex> lock(deploy->mu);
    ostringstream out;
    out << "{\n  \"id\": " << deploy->id
        << ",\n  \"status\": \"" << deploy->status
        << "\",\n  \"lines\": [\n";
    for (size_t i = 0; i < deploy->lines.size(); i++)
    {
        out << "    {\"id\": " << deploy->lines[i].id
            << ", \"text\": \"" << deploy->lines[i].text << "\"}";
        if (i + 1 < deploy->lines.size())
            out << ",";
        out << "\n";
    }
    out << "  ]\n}\n";
    return out.str();
}

int main()
{
    httplib::Server svr;

    svr.Get("/hello", [](const httplib::Request &, httplib::Response &res)
            { res.set_content("Hello from C++", "text/plain"); });

    svr.Post("/deploys", [](const httplib::Request&, httplib::Response& res){
        auto deploy = make_shared<Deploy>();
        deploy->id = next_deploy_id++;
        {
            lock_guard<mutex> lock(deploys_mu);
            deploys[deploy->id] = deploy;
        }
        thread(run_deploy, deploy).detach();
        res.set_content("{\"id\": " + to_string(deploy->id) + "}\n", "application/json");
    });

    svr.Get("/deploys/:id", [](const httplib::Request& req, httplib::Response& res) {
        long id = stol(req.path_params.at("id"));
        shared_ptr<Deploy> deploy;
        {
            lock_guard<mutex> lock(deploys_mu);
            auto it = deploys.find(id);
            if (it == deploys.end()) {
                res.status = 404;
                res.set_content("not found\n", "text/plain");
                return;
            }
            deploy = it->second;
        }
        res.set_content(snapshot(deploy), "application/json");
    });

    cout << "listening on http://localhost:8080\n";
    svr.listen("0.0.0.0", 8080);
}