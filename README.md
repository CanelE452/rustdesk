# Experimental Android direct download

The binary in this branch is the previously validated build from
GitHub Actions run 40868797058, source commit
d5d40e0c6cba8bb3a442b0ddabda18b414a9e641.

This is a phone/tablet test APK, not a verified production release.
It has a separate app ID and a debug signing certificate.
It contains experimental touchpad, F-key and app-return recovery
logic. If Google Play Protect blocks installation, stop rather
than bypass the warning.
