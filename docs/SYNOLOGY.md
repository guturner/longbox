# Hosting on a Synology DS923+

The DS923+ has an x86-64 AMD Ryzen CPU, so standard `linux/amd64` images (Temurin, Postgres) run unchanged in **Container Manager**.

## 1. Build the image (on your PC)

```bash
./gradlew :server:buildFatJar :webApp:wasmJsBrowserDistribution
docker build -f server/Dockerfile -t longbox-server:0.1.0 .
docker save longbox-server:0.1.0 | gzip > longbox-server.tar.gz
```
Container Manager → **Image → Add → Add from file** → `longbox-server.tar.gz`.

## 2. Folders and secrets on the NAS

File Station or SSH:
```
/volume1/docker/longbox/
  pgdata/            (Postgres data)
  backups/           (nightly pg_dump; include in Hyper Backup)
  secrets/db_password       one line, random 32+ chars
  secrets/metron_password   your Metron password
  docker-compose.yml        from deploy/synology/
  .env                      from deploy/synology/.env.example
```
`chmod 600 secrets/*`.

## 3. Start the project

Container Manager → **Project → Create**, path `/volume1/docker/longbox`, using the existing `docker-compose.yml`. This starts three containers: `db`, `server` and `backup`.

## 4. HTTPS on your home network

The server only listens on `127.0.0.1:8080` on the NAS. Put DSM's reverse proxy in front of it:
1. **Control Panel → Login Portal → Advanced → Reverse Proxy → Create**: source `HTTPS`, host `longbox.<your-domain>`, port 443 → destination `HTTP`, `localhost`, `8080`.
2. **Control Panel → Security → Certificate**: add a Let's Encrypt certificate for that hostname and assign it to the reverse-proxy entry. Let's Encrypt has to validate the domain; check how DSM does that for your domain before opening router ports. With Tailscale (below), `tailscale cert` issues a trusted certificate for the NAS's `*.ts.net` name without exposing anything.
3. Point the hostname at the NAS's LAN IP in your router's DNS (or a local DNS override).

HTTPS is required, not cosmetic: cookies are `Secure`, and Android blocks cleartext traffic.

## 5. Configure the clients

- **Android:** in `~/.gradle/gradle.properties`, set `longbox.serverUrl=https://longbox.<your-domain>` and `longbox.googleWebClientId=...`.
- **Windows:** create `%LOCALAPPDATA%\Longbox\longbox.properties` with `serverUrl`, `googleDesktopClientId` and `googleDesktopClientSecret`.
- **Web:** open `https://longbox.<your-domain>/`. The server serves it, and the `<meta name="google-client-id">` in `index.html` is your Web client id.

In Google Cloud Console, create three OAuth clients (Android, Desktop, Web) and list all three ids in `GOOGLE_CLIENT_IDS`.

## Roaming

Away from home, `/healthz` doesn't route, so the apps switch to the cached library plus the outbox automatically. When you're back on Wi-Fi, the outbox drains within about 30 seconds, or immediately when the app is reopened.

**Optional:** install the **Tailscale** package on the NAS and on your phone. The phone can then reach the NAS from anywhere over WireGuard, with no router port-forward, so changes sync immediately even while roaming. The offline outbox still covers dead zones. Avoid forwarding ports 80/443 to the NAS just for this app; the VPN route keeps the NAS off the public internet.
