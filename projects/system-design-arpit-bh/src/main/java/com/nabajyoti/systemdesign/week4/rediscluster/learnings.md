# Redis cluster lab notes

Docker Desktop on this Mac. Three Redis 7.4 masters. Ports `7100`–`7102`, bus `17100`–`17102`. They announce `192.168.1.3` (this Mac's current LAN address).

- **Port 7000 in use.** macOS AirPlay already listens there. Use `7100`–`7102`.
- **`cluster-announce-ip host.docker.internal` crashes Redis.** That setting must be a numeric IP.
- **`127.0.0.1` makes `cluster create` hang** on `Waiting for the cluster to join`. Inside a container, that address is the container itself. `192.168.65.254` works from Docker but the Mac cannot open it. Announce `192.168.1.3` instead. Ctrl+C the hung command.
- **Second create: `Node ... is not empty`.** No keys. The failed join left `nodes.conf` in the volume, and `up` loads it again. Delete the volumes, confirm `cluster_known_nodes:1` and `cluster_slots_assigned:0`, then create.

```bash
docker compose -f projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/rediscluster/docker-compose.yml down
docker volume rm rediscluster_redis-7100-data rediscluster_redis-7101-data rediscluster_redis-7102-data
docker compose -f projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/rediscluster/docker-compose.yml up -d
docker exec redis-7100 redis-cli --cluster create 192.168.1.3:7100 192.168.1.3:7101 192.168.1.3:7102 --cluster-replicas 0 --cluster-yes
```

Old `redis-7000` volumes are unused leftovers.
