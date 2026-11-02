#!/usr/bin/env bash
# ==============================================================================
# SmartParkingHub - Offsite Secondary Node Replication Script
# Copies latest encrypted backup from VPS 2 to VPS 1 secondary storage node.
# ==============================================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-$HOME/smartparking/docker/backup/data}"
DEST_HOST="${DEST_HOST:-103.20.102.190}"
DEST_PORT="${DEST_PORT:-2222}"
DEST_USER="${DEST_USER:-tungdeptrai}"
DEST_DIR="${DEST_DIR:-/home/tungdeptrai/smartparking/backups}"

LATEST_ENC=$(find "${BACKUP_DIR}" -name "smartparking_all_dbs_*.sql.gz.enc" | sort -r | head -n 1)

if [ -z "${LATEST_ENC}" ]; then
    echo "[ERROR] No encrypted backup found in ${BACKUP_DIR} to sync."
    exit 1
fi

echo "======================================================================"
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Starting Secondary Offsite Replication..."
echo "Source: ${LATEST_ENC}"
echo "Dest:   ${DEST_USER}@${DEST_HOST}:${DEST_DIR}/"
echo "======================================================================"

# Sync to VPS 1 using scp / rsync with SSH key if available
SSH_KEY_OPT=""
if [ -f "$HOME/.ssh/github_ed25519" ]; then
    SSH_KEY_OPT="-i $HOME/.ssh/github_ed25519"
fi

scp ${SSH_KEY_OPT} -P "${DEST_PORT}" -o StrictHostKeyChecking=no "${LATEST_ENC}" "${LATEST_ENC}.sha256" "${DEST_USER}@${DEST_HOST}:${DEST_DIR}/"

echo "[SUCCESS] Secondary offsite backup successfully synced to ${DEST_HOST}!"
