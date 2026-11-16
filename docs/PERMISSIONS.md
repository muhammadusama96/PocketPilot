# Permissions

Every permission PocketPilot requests, why, and what happens if you refuse it.

| Permission | Why | If refused |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Read the screen's UI tree; tap, swipe, type, screenshot. The whole product. | The agent cannot see or touch any app. It can only chat. |
| `SYSTEM_ALERT_WINDOW` | Floating ball / overlay UI. | No overlay; tasks run from the app screen only. |
| `FOREGROUND_SERVICE` (+ mic / media-projection types) | Keep the brain + voice running while you use other apps. | The pilot stops when the app is backgrounded. |
| `RECORD_AUDIO` | Voice input + wake word. | Voice off; type tasks instead. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read notifications for triage ("who messaged?"). | No notification reading. |
| `POST_NOTIFICATIONS` | Show the pilot's status / ask-user prompts. | No status updates. |
| `CALL_PHONE`, `READ_PHONE_STATE` | "Call Mum" tool. | No calling. |
| `SEND_SMS`, `READ_SMS` | "Text X" tool. | No SMS. |
| `READ_CALENDAR` | "What's next today?" / scheduling. | No calendar access. |
| `READ_CONTACTS` | Resolve "Mum" to a number. | Names must be typed as numbers. |
| `ACCESS_FINE_LOCATION` / `COARSE` | "Where am I?" / nearby tasks. | No location. |
| `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` | List apps for `open_app`; know the foreground app. | Apps must be opened by package name. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Cloud LLM backend + model downloads. | On-device mode only. |
| `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `WAKE_LOCK` | Scheduler + proactive loop survive reboot. | No scheduled/proactive tasks. |
| `CAMERA` (optional, not requested in v0.1) | Barcode/QR scanning. | Not requested yet. |

The accessibility service is the sensitive one: it can see anything on
screen, including banking and messages. That is why the privacy rules are
strict — see `PRIVACY.md`: screen content is never sent to a cloud model
unless you choose cloud mode, memory is encrypted, and `allowBackup=false`.
