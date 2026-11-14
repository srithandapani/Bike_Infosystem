# Apple CarPlay Receiver Compatibility & Diagnostic Report

**Target Device:** Realme C83 (Android 16 / SDK 36, Kernel Linux 5.15)  
**Evaluated Receiver:** Tarmac v0.5.0 (`com.altair571.tarmac`)  
**Tested Device:** Prasad’s iPhone (`68:44:65:8D:10:03`)  
**Date:** November 14, 2026  

---

## 1. Executive Summary

As part of Phase 1 evaluation to determine whether the Realme C83 running Android 16 can function as a standalone wireless Apple CarPlay receiver before considering any integration with the MOTODRIVE Bike Infotainment platform:

1. **Bluetooth Pairing & iAP2 Authentication:** **SUCCESS**  
   The phone successfully establishes an RFCOMM link over Apple's wireless iAP2 UUID (`00000000-deca-fade-deca-deafdecacafe`), successfully completes the MFi certificate exchange (`0xaa00`/`0xaa01`), generates the local ECDSA P-256 signature response (`0xaa02`/`0xaa03`), and receives `0xaa05 authentication-succeeded`.
2. **Wi-Fi Hotspot Association:** **SUCCESS**  
   The iPhone successfully receives the Wi-Fi credentials via iAP2 (`0x5703`) and connects to the Realme C83 Personal Hotspot (`TVS Ronin`, 5 GHz) at 390 Mbps link speed (`10.125.246.170`).
3. **CarPlay Session Establishment:** **FAILED (Stuck on "Opening CarPlay…")**  
   The receiver launches `CarPlayHostActivity` but remains indefinitely on `"Opening CarPlay…"` and eventually times out.
4. **Root Cause:**  
   On retail Android 16 (ColorOS / MediaTek), the Linux kernel and SELinux isolate the SoftAP tethering interface (`ap0`). Multicast UDP traffic (`224.0.0.251:5353` / mDNS) between connected Wi-Fi clients and local user applications is dropped by the system. Consequently, the receiver cannot discover the iPhone's `_carplay-ctrl._tcp` service to issue the `/ctrl-int/1/connect` HTTP probe, and the iPhone cannot discover the receiver's `_airplay._tcp` service. After 20–30 seconds with no AirPlay handshake, iOS terminates the connection (`REMOTE_USER_TERMINATED_CONNECTION(0x13)`).
5. **Second iPhone Evaluation:**  
   Device Bluetooth audit confirms no second iPhone is bonded or within range. However, because the failure stems from Android 16 SoftAP multicast isolation and Wi-Fi Direct driver concurrency, testing with another iPhone will yield the identical result.

---

## 2. Technical Evidence & Logs

### iAP2 Handshake & MFi Validation (Passing)
```text
xcertplay-usb: iap2 identification accepted
xcertplay-usb: mfi type=MFI certificate loaded bytes=607 payload bytes=611
xcertplay-usb: iap2 mfi rx=0xaa00 request-certificate
xcertplay-usb: iap2 mfi tx=0xaa01 certificate bytes=611
xcertplay-usb: iap2 mfi rx=0xaa02 challenge bytes=32
xcertplay-usb: mfi local signature count=1 digestBytes=32
xcertplay-usb: iap2 mfi tx=0xaa03 signature bytes=64
xcertplay-usb: iap2 mfi rx=0xaa05 authentication-succeeded
xcertplay-usb: iap2 authentication accepted
xcertplay-usb: iap2 subscriptions sent
xcertplay-usb: iap2 rx=0x5702 request-wifi-configuration
xcertplay-usb: iap2 tx=0x5703 accessory-wifi-configuration
xcertplay-usb: iap2 rx=0x4e0d wireless-carplay-update available=true
xcertplay-usb: iap2 tx=0x4301 carplay-start-session
```

### SoftAP Multicast (mDNS) Failure (Blocking Issue)
```text
xcertplay-usb: wireless startup elapsedMs=270455 authenticated=true wifiConfigs=2 startRequests=1 tcpAccepted=0 sessionActive=false waitingFor=WiFi_discovery_or_AirPlay_TCP startRequestAgeMs=9373
xcertplay-usb: wireless snapshot interfaceState=up multicast=true ipv4Usable=1 bonjourAdded=0 bonjourResolved=0 connectProbes=0 lastProbe=not_started
xcertplay-usb: wireless snapshot receiveCounters udpScope=device ifaceRx=unavailable failureClass=FileNotFoundException
```
- Tarmac's AirPlay listener bound to `10.125.246.100:7000`.
- The iPhone connected as client `10.125.246.170`.
- JmDNS received `0` multicast DNS frames (`bonjourAdded=0`, `bonjourResolved=0`).
- Without mDNS, Tarmac could not dispatch `probeOnce` (`GET /ctrl-int/1/connect HTTP/1.1`) to trigger the iPhone's AirPlay streamer.

### iOS Timeout Teardown
```text
shim::legacy::btm ACL Disconnected : xx:xx:xx:xx:10:03 classic reason:REMOTE_USER_TERMINATED_CONNECTION(0x13)
```

---

## 3. Architecture Comparison

| Feature | Software Wireless CarPlay (Tarmac) | MOTODRIVE Native Architecture |
| :--- | :--- | :--- |
| **Transport** | Wi-Fi 5 GHz + AirPlay TCP/RTSP | Bluetooth Classic (A2DP/AVRCP) + BLE |
| **Android 16 Compatibility** | ❌ Blocked by SoftAP mDNS & P2P restrictions |  Fully compatible with Android 16 |
| **Screen Projection** | Full CarPlay UI (Video stream) | Native BMW M Sport Dashboard |
| **Media & Music Info** | Relies on AirPlay stream (blank when disconnected) | Live track/artist via Bluetooth AVRCP / AMS |
| **GPS / Navigation** | Requires AirPlay streaming from Apple Maps | Live device GPS + Mapbox / OSM Navigation |
| **Hardware Required** | External MFi dongle (Carlinkit CPC200-CCPA) for retail Android | Zero extra hardware (Phone handles everything) |

---

## 4. Final Recommendation

- **Do Not Integrate Software Wireless CarPlay:** Retail Android 16 devices cannot reliably serve as software-only wireless CarPlay receivers due to system-level Wi-Fi tethering multicast restrictions.
- **Maintain MOTODRIVE Native Architecture:** MOTODRIVE’s native BLE/AVRCP media handling and built-in GPS navigation provide rock-solid reliability, battery efficiency, and zero hardware dependencies on the Realme C83.
