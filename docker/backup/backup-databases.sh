#!/usr/bin/env bash
# ==============================================================================
# SmartParkingHub - 3-2-1-1-0 Automated Encrypted Database Backup Script
# Dumps 5 Microservice databases, compresses with gzip, encrypts with AES-256-CBC,
# and enforces a 7-day local retention policy.
# ==============================================================================
set -euo pipefail

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="${BACKUP_DIR:-$HOME/smartparking/docker/backup/data}"
LOG_FILE="${BACKUP_DIR}/backup.log"
RETENTION_DAYS=7

# Load encryption key from environment or fallback secure key
BACKUP_ENCRYPTION_KEY="${BACKUP_ENCRYPTION_KEY:-SP_Backup_2026_SecPass_8xKqM99!}"
DB_ROOT_PASSWORD="${DB_PASSWORD:-SP_SecDB_2026_xK9mQz88!}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-smart-parking-mysql}"

mkdir -p "$BACKUP_DIR"

echo "======================================================================"
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Starting SmartParkingHub Automated Backup..."
echo "======================================================================"

DATABASES=(
    "smart_parking_identity_db"
    "smart_parking_lot_db"
    "smart_parking_booking_db"
    "smart_parking_payment_db"
    "smart_parking_notification_db"
)

RAW_SQL_FILE="${BACKUP_DIR}/smartparking_all_dbs_${TIMESTAMP}.sql"
GZ_FILE="${RAW_SQL_FILE}.gz"
ENC_FILE="${GZ_FILE}.enc"

# Step 1: Dump all 5 databases using single-transaction and quick mode
echo "[1/4] Dumping 5 databases from ${MYSQL_CONTAINER}..."
docker exec "${MYSQL_CONTAINER}" mysqldump \
    -u root \
    -p"${DB_ROOT_PASSWORD}" \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --databases "${DATABASES[@]}" > "${RAW_SQL_FILE}"

RAW_SIZE=$(du -h "${RAW_SQL_FILE}" | cut -f1)
echo "      Dump completed. Raw size: ${RAW_SIZE}"

# Step 2: Compress using gzip
echo "[2/4] Compressing dump with gzip..."
gzip -9 "${RAW_SQL_FILE}"
GZ_SIZE=$(du -h "${GZ_FILE}" | cut -f1)
echo "      Compression completed. Gzip size: ${GZ_SIZE}"

# Step 3: Encrypt with OpenSSL AES-256-CBC (PBKDF2)
echo "[3/4] Encrypting with OpenSSL AES-256-CBC..."
openssl enc -aes-256-cbc -salt -pbkdf2 \
    -in "${GZ_FILE}" \
    -out "${ENC_FILE}" \
    -pass "pass:${BACKUP_ENCRYPTION_KEY}"

# Securely shred/remove the unencrypted gzip file
rm -f "${GZ_FILE}"
ENC_SIZE=$(du -h "${ENC_FILE}" | cut -f1)
echo "      Encryption completed. Output: ${ENC_FILE} (${ENC_SIZE})"

# Step 4: Enforce Retention Policy (delete backups older than 7 days)
echo "[4/4] Cleaning up local backups older than ${RETENTION_DAYS} days..."
DELETED_COUNT=$(find "${BACKUP_DIR}" -name "smartparking_all_dbs_*.sql.gz.enc" -mtime "+${RETENTION_DAYS}" | wc -l)
find "${BACKUP_DIR}" -name "smartparking_all_dbs_*.sql.gz.enc" -mtime "+${RETENTION_DAYS}" -delete
echo "      Deleted ${DELETED_COUNT} outdated backup files."

# Step 5: Generate checksum for integrity verification
sha256sum "${ENC_FILE}" > "${ENC_FILE}.sha256"

# Step 6: Automatically trigger secondary offsite replication if sync-offsite.sh exists
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "${SCRIPT_DIR}/sync-offsite.sh" ]; then
    echo "[INFO] Triggering automatic secondary node replication..."
    bash "${SCRIPT_DIR}/sync-offsite.sh" || echo "[WARNING] Offsite sync encountered an issue, check network."
fi

echo "======================================================================"
echo "[SUCCESS] Backup finished successfully at $(date +'%Y-%m-%d %H:%M:%S')"
echo "          Encrypted Archive: ${ENC_FILE}"
echo "          Checksum File:     ${ENC_FILE}.sha256"
echo "======================================================================"
