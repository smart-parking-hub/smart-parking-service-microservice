#!/bin/sh
set -e

echo "[FIREWALL] Applying Network Isolation rules on VPS 3 (Core Business Services)..."

which iptables >/dev/null 2>&1 || apk add --no-cache iptables

iptables -F DOCKER-USER

# 1. Allow established and related connections
iptables -A DOCKER-USER -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# 2. Allow local loopback and internal Docker networks (127.0.0.1 and 172.16.0.0/12)
iptables -A DOCKER-USER -s 127.0.0.1 -j ACCEPT
iptables -A DOCKER-USER -s 172.16.0.0/12 -j ACCEPT

# 3. Protect core services (ParkingLot 8082, Booking 8087): Allow only VPS 1 (103.20.102.190 - Gateway)
iptables -A DOCKER-USER -p tcp -m multiport --dports 8082,8087 -s 103.20.102.190 -j ACCEPT
iptables -A DOCKER-USER -p tcp -m multiport --dports 8082,8087 -j DROP

# 4. Return for all other traffic
iptables -A DOCKER-USER -j RETURN

echo "[FIREWALL] DOCKER-USER rules successfully applied on VPS 3!"
iptables -L DOCKER-USER -n -v
