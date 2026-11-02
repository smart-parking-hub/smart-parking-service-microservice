#!/usr/bin/env bash
# ==============================================================================
# SmartParkingHub - Install Daily Automated Backup Cron Job
# Schedules backup-databases.sh to execute every night at 02:00 AM.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_SCRIPT="${SCRIPT_DIR}/backup-databases.sh"
LOG_FILE="${SCRIPT_DIR}/data/cron_backup.log"

chmod +x "${SCRIPT_DIR}"/*.sh

# Define cron line: 02:00 AM every day
CRON_JOB="0 2 * * * ${BACKUP_SCRIPT} >> ${LOG_FILE} 2>&1"

# Check if cron job is already installed
if crontab -l 2>/dev/null | grep -F "${BACKUP_SCRIPT}" >/dev/null; then
    echo "[INFO] Automated backup cron job is already installed:"
    crontab -l | grep -F "${BACKUP_SCRIPT}"
else
    (crontab -l 2>/dev/null || true; echo "${CRON_JOB}") | crontab -
    echo "[SUCCESS] Automated backup cron job installed successfully:"
    echo "          ${CRON_JOB}"
fi
