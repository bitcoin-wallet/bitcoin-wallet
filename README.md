Welcome to _Bitcoin Wallet_, a standalone Bitcoin payment app for your Android device!

You can get the app from the store of your choice:
<a href="https://f-droid.org/app/de.schildbach.wallet">F-Droid</a> | <a href='https://play.google.com/store/apps/details?id=de.schildbach.wallet'>Google Play</a> | <a href="https://github.com/bitcoin-wallet/bitcoin-wallet/releases">Direct APK download from GitHub</a>

This project contains several sub-projects:

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

You can build all sub-projects at once using Gradle:

`gradle clean build`
