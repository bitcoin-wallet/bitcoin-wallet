# BITCOIN WALLET

Welcome to _Bitcoin Wallet_, a standalone Bitcoin payment app for your Android device!

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


### PREREQUISITES FOR BUILDING

You'll need git, a Java 8 SDK (or later) and Gradle 4.4 (or later) for this. We'll assume Ubuntu 18.04 LTS (Bionic Beaver)
for the package installs, which comes with OpenJDK 8 and Gradle 4.4.1 out of the box.

    # first time only
    sudo apt install git gradle openjdk-8-jdk

Create a directory for the Android SDK (e.g. `android-sdk`) and point the `ANDROID_HOME` variable to it.

Download the [Android SDK Tools](https://developer.android.com/studio/index.html#command-tools)
and unpack it to `$ANDROID_HOME/`.

Finally, the last preparative step is acquiring the source code. Again in your workspace, use:

    # first time only
    git clone -b master https://github.com/bitcoin-wallet/bitcoin-wallet.git bitcoin-wallet
    cd bitcoin-wallet


### BUILDING

You can build all sub-projects in all flavors at once using Gradle:

    # each time
    gradle clean build

For details about building the wallet see the [specific README](wallet/README.md).


### BINARIES

You can install the app from the app store of your choice:

 * __Testnet__:
   <a href="https://f-droid.org/app/de.schildbach.wallet_test">F-Droid</a> |
   <a href='https://play.google.com/store/apps/details?id=de.schildbach.wallet_test'>Google Play</a>
 * __Mainnet__:
   <a href="https://f-droid.org/app/de.schildbach.wallet">F-Droid</a> |
   <a href='https://play.google.com/store/apps/details?id=de.schildbach.wallet'>Google Play</a>

<a href="https://f-droid.org/app/de.schildbach.wallet"><img alt="Get it on F-Droid" src="https://f-droid.org/wiki/images/5/55/F-Droid-button_get-it-on_bigger.png" height="80pt"/></a>&nbsp;&nbsp;<a href="https://play.google.com/store/apps/details?id=de.schildbach.wallet&utm_source=global_co&utm_medium=prtnr&utm_content=Mar2515&utm_campaign=PartBadge&pcampaignid=MKT-AC-global-none-all-co-pr-py-PartBadges-Oct1515-1"><img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/apps/en-play-badge.png" height="80pt"/></a>
