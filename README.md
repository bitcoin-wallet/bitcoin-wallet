# GROESTLCOIN WALLET

Welcome to _Groestlcoin Wallet_, a standalone GroestlCoin payment app for your Android device!

This project contains several sub-projects:

 * __wallet__:
     The Android app itself. This is probably what you're searching for.
 * __market__:
     App description and promo material for the Google Play app store.
 * __integration-android__:
     A tiny library for integrating digitial payments into your own Android app
     (e.g. donations, in-app purchases).
 * __sample-integration-android__:
     A minimal example app to demonstrate integration of digital payments into
     your Android app.


### PREREQUISITES FOR BUILDING

You'll need git, a Java 8 SDK (or later) and Gradle 4.4 (or later) for this. We'll assume Ubuntu 20.04 LTS (Focal Fossa)
for the package installs, which comes with OpenJDK 8 and Gradle 4.4.1 out of the box.

    # first time only
    sudo apt install git gradle openjdk-8-jdk

Create a directory for the Android SDK (e.g. `android-sdk`) and point the `ANDROID_HOME` variable to it.

Download the [Android SDK Tools](https://developer.android.com/studio/index.html#command-tools)
and unpack it to `$ANDROID_HOME/`.

Finally, the last preparative step is acquiring the source code. Again in your workspace, use:

    # first time only
    git clone -b master https://github.com/Groestlcoin/groestlcoin-wallet.git groestlcoin-wallet
    cd groestlcoin-wallet


### BUILDING

You can build all sub-projects in all flavors at once using Gradle:

    # each time
    gradle clean build

For details about building the wallet see the [specific README](wallet/README.md).

### Stores
 * __Testnet__:
   <a href="https://f-droid.org/app/hashengineering.groestlcoin.wallet_test">F-Droid</a> |
   <a href='https://play.google.com/store/apps/details?id=hashengineering.groestlcoin.wallet_test'>Google Play</a>
 * __Mainnet__:
   <a href="https://f-droid.org/app/hashengineering.groestlcoin.wallet">F-Droid</a> |
   <a href='https://play.google.com/store/apps/details?id=hashengineering.groestlcoin.wallet'>Google Play</a>
