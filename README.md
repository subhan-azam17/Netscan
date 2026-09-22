# Netscan
Install on the phone, allow "install from unknown sources" when prompted, and make sure the phone is on the Wi-Fi you want to scan — the target field auto-fills with your subnet.
| nmap flag    | NetScan feature                                                                                          |
| ------------ | -------------------------------------------------------------------------------------------------------- |
| `-sn`        | **Host discovery**: ICMP ping sweep of a whole subnet (uses the system `ping` binary, so no root needed) |
| `-sT`        | **TCP connect scan** of any port list/range, 64 parallel connections per host, hosts scanned in parallel |
| `-sV` (lite) | **Banner grabbing** — reads service banners, sends `HEAD /` for HTTP ports (80/8000/8080/8888)           |
| `-p`         | Port spec parsing: `1-1024`, `22,80,443,8080`, mixed lists                                               |
Project structure:
ScannerEngine.kt — the core: local subnet auto-detection from Wi-Fi DHCP info, ping sweep, parsePorts(), semaphore-limited connect-scan, HTTP probe + banner reader
MainActivity.kt — UI: target field (single IP or 192.168.1.0/255.255.255.0), port field, "ping sweep first" checkbox, start/stop, live results list with progress
ResultAdapter.kt + layouts, manifest with INTERNET / ACCESS_WIFI_STATE permissions
