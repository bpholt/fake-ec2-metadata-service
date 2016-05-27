#!/usr/bin/env bash
set -euo pipefail

INTERFACE_CFG=/etc/network/interfaces

if ! ( fgrep "source interfaces.d" ${INTERFACE_CFG} || fgrep "source-directory interfaces.d" ${INTERFACE_CFG} ); then
  echo "source interfaces.d/*.cfg" >> ${INTERFACE_CFG}
fi

mkdir -p /etc/network/interfaces.d
cat << __EOF__ > /etc/network/interfaces.d/fake-ec2-metadata-service.cfg
auto lo:1
iface lo:1 inet static
  address 169.254.169.254
  netmask 255.255.255.255
__EOF__

ifup -a

if ! ifconfig | fgrep 169.254.169.254 > /dev/null 2>&1; then
  echo "Network setup failed; loopback interface on 169.254.169.254 not available." > /dev/stderr
  exit 1
fi

