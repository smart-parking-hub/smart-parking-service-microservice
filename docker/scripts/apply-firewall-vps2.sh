#!/bin/sh
set -e

echo "[FIREWALL] Applying Network Isolation rules on VPS 2 (Data & Async Services)..."

which iptables >/dev/null 2>&1 || apk add --no-cache iptables

iptables -F DOCKER-USER

# 1. Allow established and related connections
iptables -A DOCKER-USER -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# 2. Allow local loopback and internal Docker networks (127.0.0.1 and 172.16.0.0/12)
iptables -A DOCKER-USER -s 127.0.0.1 -j ACCEPT
iptables -A DOCKER-USER -s 172.16.0.0/12 -j ACCEPT

# 3. Protect MySQL 3306: Allow only VPS 1 (103.20.102.190) and VPS 3 (103.20.102.137)
iptables -A DOCKER-USER -p tcp --dport 3306 -s 103.20.102.190 -j ACCEPT
iptables -A DOCKER-USER -p tcp --dport 3306 -s 103.20.102.137 -j ACCEPT
iptables -A DOCKER-USER -p tcp --dport 3306 -j DROP

# 4. Protect Kafka 9092: Allow only VPS 3 (103.20.102.137)
iptables -A DOCKER-USER -p tcp --dport 9092 -s 103.20.102.137 -j ACCEPT
iptables -A DOCKER-USER -p tcp --dport 9092 -j DROP

# 5. Protect microservices (Payment 8084, Notification 8085, AI 8086): Allow only VPS 1 (103.20.102.190 - Gateway)
iptables -A DOCKER-USER -p tcp -m multiport --dports 8084,8085,8086 -s 103.20.102.190 -j ACCEPT
iptables -A DOCKER-USER -p tcp -m multiport --dports 8084,8085,8086 -j DROP

# 6. Return for all other traffic
iptables -A DOCKER-USER -j RETURN

echo "[FIREWALL] DOCKER-USER rules successfully applied on VPS 2!"
iptables -L DOCKER-USER -n -v
