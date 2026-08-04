# Arcana Android deeplink

<!--- TOC -->

* [Introduction](#introduction)
  * [Asset Links](#asset-links)
  * [Supported links](#supported-links)
* [Developer tools](#developer-tools)

<!--- END -->


## Introduction

Arcana Android supports deep linking to specific screens in the application. This document explains how to use deep links in Arcana Android.

### Asset Links

The asset links file is available at https://arcana.celesteai.ru/.well-known/assetlinks.json

### Supported links

Arcana invite (App Link):
> https://arcana.celesteai.ru/invite/<token>

Arcana invite (custom scheme):
> arcana://invite/<token>

Notification / in-app navigation:
> arcana://open/<sessionId>
> arcana://open/<sessionId>/<roomId>
> arcana://open/<sessionId>/<roomId>/<threadId>
> arcana://open/<sessionId>/<roomId>/<threadId>/<eventId>

Permalink custom schemes (from matrix.to):
> arcana://user/@alice:matrix.org
> arcana://room/!roomid:matrix.org

Matrix URI scheme:
> matrix:u/alice:matrix.org
> matrix:r/roomid:matrix.org

Element Call (custom schemes; widget host remains call.element.io):
> arcana://call?url=https%3A%2F%2Fcall.element.io%2FExample
> io.element.call:/?url=https%3A%2F%2Fcall.element.io%2FExample

## Developer tools

Using an Android 12 or higher emulator

Ensure links verification is enabled
```bash
adb shell am compat enable 175408749 ru.celesteai.arcana.debug  
```

Reset link verifications for the given package id
```bash
adb shell pm set-app-links --package ru.celesteai.arcana.debug 0 all 
```

Force the package id links to be verified
```bash
adb shell pm verify-app-links --re-verify ru.celesteai.arcana.debug 
```

Print the link verification of the package id
```bash
adb shell pm get-app-links ru.celesteai.arcana.debug
```

```
  ru.celesteai.arcana.debug:
    ID: <verification-id>
    Signatures: [<sha256-cert-fingerprint>]
    Domain verification state:
      arcana.celesteai.ru: verified
```
