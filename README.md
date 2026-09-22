# NetScan

An nmap-style network scanner for Android (no root required).

- **Host discovery**: ICMP ping sweep of a subnet (nmap -sn)
- **Port scanning**: TCP connect scan with parallel workers (nmap -sT)
- **Service probes**: banner grabbing + HTTP HEAD probe (nmap -sV lite)

## Get the APK

### Option A - GitHub Actions (no PC tools needed)
1. Create a new repository on github.com and upload these files (or `git push`).
2. Go to the **Actions** tab -> "Build APK" -> **Run workflow**.
3. When it finishes, open the run -> **Artifacts** -> download `netscan-apk` -> the `.apk` is inside.

### Option B - Android Studio
1. Open the project folder in Android Studio, let Gradle sync.
2. **Build > Build Bundle(s)/APK(s) > Build APK(s)**.
3. Click "locate" to find `app-debug.apk`.

## Usage
- Target field accepts a single IP (`192.168.1.1`) or a subnet (`192.168.1.0/255.255.255.0`).
- Ports accept ranges/lists: `1-1024`, `22,80,443`, or mixed.
- Enable "Ping sweep" to discover live hosts before scanning (much faster on big subnets).

Only scan networks you own or are authorized to test.
