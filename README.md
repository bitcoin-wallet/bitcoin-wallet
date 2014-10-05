## Welcome to _Bitcoin Wallet_

__What is _Bitcoin Wallet_?__

It is a standalone Bitcoin payment app for your Android device!


__This project contains several sub-projects:__

 * __wallet__:
     The Android app itself. This is probably what you're searching for.
 * __market__:
     App description and promo material for the Google Play app store.
 * __integration-android__:
     A tiny library for integrating Bitcoin payments into your own Android app
     (e.g. donations, in-app purchases).
 * __sample-integration-android__:
     A minimal example app to demonstrate integration of Bitcoin payments into
     your Android app.


__You can build all sub-projects at once using Maven:__

`mvn clean install`


__You can submit translations:__

Changes to translations as well as new translations can be submitted to
[Bitcoin Wallet's Transifex page](https://www.transifex.com/projects/p/bitcoin-wallet/).

Translations are periodically pulled from Transifex and merged into the git repository.

Important: We do not accept translation changes as GitHub pull requests because the next
pull from Transifex would automatically overwrite them again.
