CXX = c++
CXXFLAGS = -std=c++17 -O2 -pthread

server: server.cpp httplib.h
	$(CXX) $(CXXFLAGS) -o server server.cpp

clean:
	rm -f server