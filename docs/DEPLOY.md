# Deploying LegaldocsGPT for free

The full platform runs on a single machine with ~12 GB of RAM:

```
                    https://your-domain
                           │
                     ┌─────▼─────┐   automatic Let's Encrypt HTTPS
                     │   Caddy   │
                     └─┬───┬───┬─┘
             /         │   │   │  /onlyoffice/*
        ┌──────────────┘   │   └──────────────┐
   ┌────▼─────┐      /api/*│            ┌─────▼──────┐
   │ Next.js  │      ┌─────▼──────┐     │ OnlyOffice │
   │ frontend │      │ API gateway│     │ Doc Server │
   └──────────┘      └─────┬──────┘     └────────────┘
                 auth · documents · worker · storage · templates
          Postgres ×2 · MongoDB · Redis · RabbitMQ · MinIO · Gotenberg
```

Everything is served from **one origin**, so there is no CORS setup and the auth cookie just works.
Typical memory use is 6–7 GB. OnlyOffice is the largest piece at roughly 2 GB.

---

## Option A: Oracle Cloud Always Free (recommended, always-on link)

As of June 2026 Oracle's Always Free tier includes an **Ampere A1 (ARM) VM with 2 OCPU / 12 GB RAM** and
~200 GB of disk. That is enough for this stack. Oracle asks for a card to verify identity, but Always Free
resources are not billed.

### 1. Create the VM (about 10 minutes)

1. Sign up at <https://www.oracle.com/cloud/free/>. Pick a home region close to you; it can't be changed later.
2. **Compute → Instances → Create instance**
   - Image: **Canonical Ubuntu 24.04** (or 22.04)
   - Shape: **Ampere → VM.Standard.A1.Flex**, **2 OCPU, 12 GB**
   - Networking: keep "Assign a public IPv4 address" on
   - Add your SSH public key (or let Oracle generate one and download it)
   - Boot volume: 100 GB is plenty
   - If you get *"Out of host capacity"*, try another Availability Domain or retry later.
3. **Open ports 80 and 443** in the cloud firewall:
   *Instance → Subnet → Default Security List → Add Ingress Rules*:
   source `0.0.0.0/0`, TCP, destination ports `80,443`.
   (The setup script opens the VM's own iptables firewall for you.)

### 2. Get a domain (free)

Choose one:
- **Zero setup:** `<ip-with-dashes>.sslip.io`, e.g. `129-213-10-20.sslip.io`. If you pass no domain, the script picks this automatically.
- **Nicer link for a CV:** create a free subdomain at <https://www.duckdns.org> (e.g. `legaldocs-alex.duckdns.org`)
  and point it at the VM's public IP.

### 3. Get an AI key (free)

Create a Groq API key at <https://console.groq.com/keys>. The free tier is plenty for a demo.

### 4. Run the setup script

SSH into the VM (`ssh ubuntu@<public-ip>`) and run:

```bash
git clone https://github.com/AlexBoiko412/legaladocsgpt_backend.git ~/legaldocs/backend
bash ~/legaldocs/backend/deploy/setup-server.sh legaldocs-alex.duckdns.org gsk_your_groq_key
```

The script:
1. installs Docker,
2. adds 4 GB of swap,
3. opens ports 80/443 in iptables,
4. clones both repos into `~/legaldocs`,
5. writes `~/legaldocs/backend/.env` with randomly generated secrets,
6. builds and starts everything with `docker-compose.deploy.yml`.

The first build takes **10–20 minutes** on the ARM VM. OnlyOffice needs another 1–2 minutes after that to
finish its own start-up. When the script finishes it prints the URL, the demo login and the admin password.

> If you log out and back in after the script installs Docker, you can run `docker` without `sudo`.

### 5. Share it

Put `https://your-domain` on your CV. Recruiters click **"Try the demo account"** on the login page,
with no sign-up needed. The demo credentials are `DEMO_EMAIL` / `DEMO_PASSWORD` in `.env`.

---

## Day-2 operations

```bash
cd ~/legaldocs/backend
docker compose -f docker-compose.deploy.yml ps                 # status / health
docker compose -f docker-compose.deploy.yml logs -f --tail=100 document-worker
bash deploy/setup-server.sh                                    # pull latest code + rebuild
docker compose -f docker-compose.deploy.yml down               # stop (data volumes are kept)
```

**Idle reclamation:** Oracle may reclaim an Always Free VM that is idle for 7 days (CPU, network *and* memory
all under 20%). This stack keeps memory use around 50%, so it doesn't count as idle.

### Optional extras

| Feature | What to set in `.env` |
|---|---|
| Google sign-in | `GOOGLE_AUTH_ENABLED=true`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, plus redirect URI `https://DOMAIN/api/auth/login/oauth2/code/google` in Google Cloud Console |
| Password-reset emails | `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` (any SMTP: Brevo free tier, Gmail app password, …) |
| OpenAI instead of Groq | `AI_PROVIDER=openai`, `OPENAI_API_KEY` |

After changing `.env`, run `docker compose -f docker-compose.deploy.yml up -d --build`. The rebuild matters
because `GOOGLE_AUTH_ENABLED` and the demo credentials are compiled into the frontend.

---

## Option B: your own PC + Cloudflare Tunnel (free, link works while the PC is on)

If you can't get an Oracle VM, run the same stack on your machine with Docker Desktop (give it at least
10 GB RAM in *Settings → Resources*) and expose it through a free Cloudflare Tunnel, which provides HTTPS.

1. Create a free Cloudflare account and a **named tunnel** (*Zero Trust → Networks → Tunnels*).
   Give it a public hostname (e.g. `legaldocs.your-domain.com`) pointing to `http://localhost:80`.
2. Clone both repos side by side (`.../legaldocs/backend` and `.../legaldocs/frontend`).
3. In the backend folder, copy `deploy/.env.deploy.example` to `.env` and fill in the secrets
   (any random strings, see the comments), plus:
   ```
   DOMAIN=legaldocs.your-domain.com
   SITE_ADDRESS=:80
   GROQ_API_KEY=gsk_...
   ```
4. `docker compose -f docker-compose.deploy.yml up -d --build`, then start the tunnel connector Cloudflare shows you.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Browser shows a certificate error right after first start | Caddy is still obtaining the certificate. Check that ports 80/443 are open in the Oracle security list and wait a minute. |
| Editor shows "Download failed" / spinner forever | OnlyOffice is still starting. Check `docker compose … logs onlyoffice-ds`. |
| Document stays "PENDING" then "FAILED" | Check `GROQ_API_KEY` and `docker compose … logs document-worker`. |
| A service keeps restarting (exit 137) | It ran out of memory. Check `docker stats` and raise its `memory:` limit in `docker-compose.deploy.yml`. |
