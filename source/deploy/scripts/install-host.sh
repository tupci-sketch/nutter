#!/usr/bin/env bash
#
# Prepares a fresh Ubuntu server for Habnut. Safe to run again.
#
# What the production server at 57.129.168.71 was given, written down so a new
# or rebuilt server gets the same:
#   - Docker from Docker's own repository, with capped logs and live-restore
#     (containers keep running while Docker itself is upgraded)
#   - a firewall that lets in SSH and nothing else: visitors arrive through
#     Cloudflare's tunnel, which dials out, so no web port is ever open
#   - fail2ban on SSH, automatic security updates
#   - SSH by key only, no root login — run this only once a key login works
#   - 2 GB of swap, if there is none
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "run as root (sudo)" >&2; exit 1; }

. /etc/os-release
export DEBIAN_FRONTEND=noninteractive

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin \
  docker-compose-plugin unzip zip ufw fail2ban unattended-upgrades git tmux curl

cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {"max-size": "10m", "max-file": "3"},
  "live-restore": true
}
EOF
systemctl restart docker

ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw --force enable

systemctl enable --now fail2ban

if [ -s "${SUDO_USER:+/home/$SUDO_USER}/.ssh/authorized_keys" ] || [ -s /root/.ssh/authorized_keys ]; then
  # sshd keeps the first value it reads, so this has to sort before the
  # cloud image's own 50-cloud-init.conf, which turns passwords on.
  printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin no\n' \
    > /etc/ssh/sshd_config.d/01-habnut.conf
  sshd -t && systemctl reload ssh
else
  echo "no authorized SSH key found: leaving password logins on" >&2
fi

if [ -z "$(swapon --show)" ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
# The hotel and the database should stay in memory; swap is for emergencies.
sysctl -qw vm.swappiness=10
echo 'vm.swappiness=10' > /etc/sysctl.d/90-habnut.conf

echo "Host ready: $(docker --version)"
