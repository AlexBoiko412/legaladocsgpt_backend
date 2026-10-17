#!/usr/bin/env bash
# Sets up a fresh Ubuntu server and starts the stack. Safe to re-run (pulls and rebuilds).
#   bash deploy/setup-server.sh <domain> <groq_api_key>
set -euo pipefail

DOMAIN="${1:-}"
GROQ_KEY="${2:-}"
BRANCH="${BRANCH:-dev}"
BASE="${BASE:-$HOME/legaldocs}"
BACKEND_REPO="${BACKEND_REPO:-https://github.com/AlexBoiko412/legaladocsgpt_backend.git}"
FRONTEND_REPO="${FRONTEND_REPO:-https://github.com/AlexBoiko412/legaladocsgpt_frontend.git}"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

if [[ -z "$DOMAIN" ]]; then
  PUBLIC_IP="$(curl -fsS https://api.ipify.org || true)"
  if [[ -n "$PUBLIC_IP" ]]; then
    DOMAIN="${PUBLIC_IP//./-}.sslip.io"
    log "No domain given - using $DOMAIN (sslip.io maps it to $PUBLIC_IP)"
  else
    echo "Usage: $0 <domain> [groq_api_key]"; exit 1
  fi
fi

# 1. Docker
if ! command -v docker >/dev/null 2>&1; then
  log "Installing Docker"
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi
DOCKER="docker"; docker info >/dev/null 2>&1 || DOCKER="sudo docker"

# 2. Swap (OnlyOffice + 6 JVMs build/start much more reliably with it)
if ! swapon --show | grep -q .; then
  log "Adding 4 GB swap"
  sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# 3. Firewall: Oracle's Ubuntu images block 80/443 with iptables by default
log "Opening ports 80 and 443 in the host firewall"
for p in 80 443; do
  sudo iptables -C INPUT -p tcp --dport "$p" -j ACCEPT 2>/dev/null \
    || sudo iptables -I INPUT 1 -p tcp -m state --state NEW --dport "$p" -j ACCEPT
done
if command -v netfilter-persistent >/dev/null 2>&1; then sudo netfilter-persistent save >/dev/null; fi

# 4. Code
log "Fetching code into $BASE"
mkdir -p "$BASE"
for pair in "backend|$BACKEND_REPO" "frontend|$FRONTEND_REPO"; do
  dir="${pair%%|*}"; url="${pair#*|}"
  if [[ -d "$BASE/$dir/.git" ]]; then
    git -C "$BASE/$dir" fetch -q origin "$BRANCH" && git -C "$BASE/$dir" checkout -q "$BRANCH" && git -C "$BASE/$dir" pull -q --ff-only
  else
    git clone -q -b "$BRANCH" "$url" "$BASE/$dir"
  fi
done
cd "$BASE/backend"

# 5. .env with generated secrets (only created once)
if [[ ! -f .env ]]; then
  log "Generating .env with random secrets"
  cp deploy/.env.deploy.example .env
  set_kv() { sed -i "s|^$1=.*|$1=$2|" .env; }
  set_kv DOMAIN "$DOMAIN"
  set_kv GROQ_API_KEY "$GROQ_KEY"
  set_kv POSTGRES_PASSWORD "$(openssl rand -hex 16)"
  set_kv JWT_SECRET "$(openssl rand -base64 48 | tr -d '\n')"
  set_kv MINIO_ROOT_PASSWORD "$(openssl rand -hex 16)"
  set_kv RABBITMQ_PASSWORD "$(openssl rand -hex 16)"
  set_kv EDIT_TOKEN_SECRET "$(openssl rand -hex 32)"
  set_kv ONLYOFFICE_JWT_SECRET "$(openssl rand -hex 32)"
  set_kv ADMIN_PASSWORD "$(openssl rand -hex 8)"
  chmod 600 .env
else
  log ".env already exists - keeping it"
  [[ -n "$GROQ_KEY" ]] && sed -i "s|^GROQ_API_KEY=.*|GROQ_API_KEY=$GROQ_KEY|" .env
fi
grep -q '^GROQ_API_KEY=.\+' .env || grep -q '^OPENAI_API_KEY=.\+' .env \
  || echo "WARNING: no AI key in .env - document generation will fail until you add GROQ_API_KEY"

# 6. Build & start
log "Building images and starting the stack (first run: ~10-20 min on ARM)"
$DOCKER compose -f docker-compose.deploy.yml up -d --build

log "Waiting for the API gateway to become healthy"
for i in $(seq 1 60); do
  status="$($DOCKER inspect -f '{{.State.Health.Status}}' legaldocs-api-gateway-1 2>/dev/null || true)"
  [[ "$status" == "healthy" ]] && break
  sleep 10
done
$DOCKER compose -f docker-compose.deploy.yml ps

DOMAIN_NOW="$(grep '^DOMAIN=' .env | cut -d= -f2)"
ADMIN_PW="$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2)"
DEMO_E="$(grep '^DEMO_EMAIL=' .env | cut -d= -f2)"
DEMO_P="$(grep '^DEMO_PASSWORD=' .env | cut -d= -f2)"
cat <<MSG

  LegaldocsGPT is starting at:  https://$DOMAIN_NOW
     (OnlyOffice needs another 1-2 minutes after first boot)

     Demo login : $DEMO_E / $DEMO_P
     Admin login: admin / $ADMIN_PW

     Logs:    cd $BASE/backend && $DOCKER compose -f docker-compose.deploy.yml logs -f --tail=100
     Update:  bash $BASE/backend/deploy/setup-server.sh
MSG
