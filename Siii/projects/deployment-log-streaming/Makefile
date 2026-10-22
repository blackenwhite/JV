CXX = c++
CXXFLAGS = -std=c++17 -O2 -pthread

server: server.cpp httplib.h
	$(CXX) $(CXXFLAGS) -o server server.cpp

ws_client: ws_client.cpp httplib.h
	$(CXX) $(CXXFLAGS) -o ws_client ws_client.cpp

clean:
	rm -f server